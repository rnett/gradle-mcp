package dev.rnett.gradle.mcp.repl

import org.slf4j.LoggerFactory
import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.util.Base64
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

class ResultRenderer(val classLoader: ClassLoader) {
    @PublishedApi
    internal val LOGGER by lazy { LoggerFactory.getLogger(ResultRenderer::class.java) }

    @Suppress("NOTHING_TO_INLINE")
    fun renderResult(value: Any?, mime: String? = null): ReplResponse.Data {
        LOGGER.info("Rendering result with {} type with mime {}", value?.let { it::class }, mime)
        if (mime != null) {
            LOGGER.info("Mime set to {}", mime)
            val stringValue = when (value) {
                null -> "null"
                is Unit -> "Unit"
                is ByteArray -> Base64.getEncoder().encodeToString(value)
                else -> value.toString()
            }
            return ReplResponse.Data(stringValue, mime)
        }

        if (value == null) return ReplResponse.Data("null", "text/plain")
        if (value is Unit) return ReplResponse.Data("Unit", "text/plain")

        if (value is ReplResponse.Data) return value

        val klass = value::class

        // AWT BufferedImage
        if (klass.isOrHasSupertypeNamed("java.awt.image.BufferedImage")) {
            LOGGER.info("Responding with BufferedImage")
            try {
                val bufferedImage = value as BufferedImage
                return renderBufferedImage(bufferedImage, mime = mime)
            } catch (e: Exception) {
                LOGGER.error("Failed to convert BufferedImage to base64", e)
                // fall back
            }
        }

        // Compose ImageBitmap
        if (klass.isOrHasSupertypeNamed("androidx.compose.ui.graphics.ImageBitmap")) {
            val bitmapClass = classLoader.loadClass("androidx.compose.ui.graphics.ImageBitmap")
            try {
                val convertersClass = classLoader.loadClass("androidx.compose.ui.graphics.DesktopConvertersKt")
                val toAwtImageMethod = convertersClass.getMethod("toAwtImage", bitmapClass)
                toAwtImageMethod.isAccessible = true
                val bufferedImage = toAwtImageMethod.invoke(null, value) as BufferedImage
                LOGGER.info("Responding with ImageBitmap converted to BufferedImage using toAwtImage")
                return renderBufferedImage(bufferedImage)
            } catch (e: Exception) {
                LOGGER.debug("Failed to convert ImageBitmap to BufferedImage, trying to use readPixels ${e.stackTraceToString()}", e)
                // fall back to readPixels
                try {
                    LOGGER.info("Responding with ImageBitmap converted to BufferedImage using readPixels")
                    val getWidthMethod = klass.java.getMethod("getWidth")
                    getWidthMethod.isAccessible = true
                    val getHeightMethod = klass.java.getMethod("getHeight")
                    getHeightMethod.isAccessible = true
                    val width = getWidthMethod.invoke(value) as Int
                    val height = getHeightMethod.invoke(value) as Int

                    LOGGER.info("Methods: {}", klass.java.methods.toList())

                    val readPixelsMethod = klass.java.methods.single { it.name == "readPixels" }
                    readPixelsMethod.isAccessible = true
                    val buffer = IntArray(width * height)
                    readPixelsMethod.invoke(value, buffer, 0, 0, width, height, 0, width)
                    val bufferedImage = toAwtImage(buffer, width, height)
                    return renderBufferedImage(bufferedImage)
                } catch (e2: Exception) {
                    LOGGER.error("Failed to convert ImageBitmap to BufferedImage, trying to use readPixels ${e.stackTraceToString()}", e)
                    LOGGER.error("Failed to convert ImageBitmap to BufferedImage using readPixels", e2)
                    // fall back
                }
            }
        }

        // Android Bitmap
        if (klass.isOrHasSupertypeNamed("android.graphics.Bitmap")) {
            try {
                val compressFormatClass = classLoader.loadClass("android.graphics.Bitmap\$CompressFormat")
                val pngFormat = compressFormatClass.getField("PNG").get(null)
                val baos = java.io.ByteArrayOutputStream()
                val compressMethod = klass.java.getMethod("compress", compressFormatClass, Int::class.java, java.io.OutputStream::class.java)
                compressMethod.isAccessible = true
                compressMethod.invoke(value, pngFormat, 100, baos)
                val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                LOGGER.info("Responding with Bitmap converted to base64")
                return ReplResponse.Data(base64, "image/png")
            } catch (e: Exception) {
                LOGGER.error("Failed to convert Bitmap to base64", e)
                // fall back
            }
        }

        if (value is ByteArray) {
            // Check if it's a common image header
            val mime = when {
                value.size > 4 && value[0] == 0x89.toByte() && value[1] == 'P'.code.toByte() && value[2] == 'N'.code.toByte() && value[3] == 'G'.code.toByte() -> "image/png"
                value.size > 3 && value[0] == 0xFF.toByte() && value[1] == 0xD8.toByte() && value[2] == 0xFF.toByte() -> "image/jpeg"
                value.size > 3 && value[0] == 'G'.code.toByte() && value[1] == 'I'.code.toByte() && value[2] == 'F'.code.toByte() -> "image/gif"
                else -> null
            }
            if (mime != null) {
                LOGGER.info("Byte array looks like image with mime type $mime")
                return ReplResponse.Data(Base64.getEncoder().encodeToString(value), mime)
            }
            LOGGER.info("Byte array does not look like an image, responding with text/plain")
            return ReplResponse.Data(value.contentToString(), "text/plain")
        }

        if (value is String) {
            return ReplResponse.Data(value, "text/plain")
        }

        return ReplResponse.Data(value.toString(), "text/plain")
    }

    fun renderBufferedImage(value: BufferedImage, mime: String? = null): ReplResponse.Data {
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(value, "png", baos)
        val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
        return ReplResponse.Data(base64, mime ?: "image/png")
    }

    fun KClass<*>.isOrHasSupertypeNamed(name: String) = this.qualifiedName == name || this.allSuperclasses.any { it.qualifiedName == name }

    /**
     * Used with pixels arrays from ImageBitmap.readPixels
     */
    fun toAwtImage(pixels: IntArray, width: Int, height: Int): BufferedImage {

        val a = 0xff shl 24
        val r = 0xff shl 16
        val g = 0xff shl 8
        val b = 0xff shl 0
        val bitMasks = intArrayOf(r, g, b, a)
        val sm = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, bitMasks)
        val db = DataBufferInt(pixels, pixels.size)
        val wr = Raster.createWritableRaster(sm, db, Point())
        return BufferedImage(ColorModel.getRGBdefault(), wr, false, null)
    }
}
