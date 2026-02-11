package dev.rnett.gradle.mcp.repl

import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.util.Base64

object ResultRenderer {

    fun renderResult(value: Any?, mime: String? = null): ReplResponse.Data {
        if (mime != null) {
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

        val className = value.javaClass.name

        // AWT BufferedImage
        if (className == "java.awt.image.BufferedImage") {
            try {
                val bufferedImage = value as BufferedImage
                val baos = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(bufferedImage, "png", baos)
                val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                return ReplResponse.Data(base64, "image/png")
            } catch (e: Exception) {
                // fall back
            }
        }

        // Compose ImageBitmap
        if (className == "androidx.compose.ui.graphics.ImageBitmap") {
            try {
                val convertersClass = Class.forName("androidx.compose.ui.graphics.DesktopConvertersKt")
                val toAwtImageMethod = convertersClass.getMethod("toAwtImage", value.javaClass)
                val bufferedImage = toAwtImageMethod.invoke(null, value) as java.awt.image.BufferedImage
                return renderResult(bufferedImage)
            } catch (e: Exception) {
                // fall back to readPixels
                try {
                    val getWidthMethod = value.javaClass.getMethod("getWidth")
                    val getHeightMethod = value.javaClass.getMethod("getHeight")
                    val width = getWidthMethod.invoke(value) as Int
                    val height = getHeightMethod.invoke(value) as Int

                    val readPixelsMethod = value.javaClass.getMethod(
                        "readPixels",
                        IntArray::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java
                    )
                    val buffer = IntArray(width * height)
                    readPixelsMethod.invoke(value, buffer, 0, 0, width, height, 0, width)
                    val bufferedImage = toAwtImage(buffer, width, height)
                    return renderResult(bufferedImage)
                } catch (e2: Exception) {
                    // fall back
                }
            }
        }

        // Android Bitmap
        if (className == "android.graphics.Bitmap") {
            try {
                val compressFormatClass = Class.forName("android.graphics.Bitmap\$CompressFormat")
                val pngFormat = compressFormatClass.getField("PNG").get(null)
                val baos = java.io.ByteArrayOutputStream()
                val compressMethod = value.javaClass.getMethod("compress", compressFormatClass, Int::class.java, java.io.OutputStream::class.java)
                compressMethod.invoke(value, pngFormat, 100, baos)
                val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                return ReplResponse.Data(base64, "image/png")
            } catch (e: Exception) {
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
                return ReplResponse.Data(Base64.getEncoder().encodeToString(value), mime)
            }
            return ReplResponse.Data(value.contentToString(), "text/plain")
        }

        if (value is String) {
            return ReplResponse.Data(value, "text/plain")
        }

        return ReplResponse.Data(value.toString(), "text/plain")
    }

    /**
     * Used with pixels arrays from ImageBitmap.readPixels
     */
    private fun toAwtImage(pixels: IntArray, width: Int, height: Int): BufferedImage {

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
