package dev.rnett.gradle.mcp

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.data.MutableDataSet
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup

interface MarkdownService : AutoCloseable {
    suspend fun downloadAsMarkdown(url: String): String
}

class DefaultMarkdownService(private val client: HttpClient = HttpClient()) : MarkdownService {
    override suspend fun downloadAsMarkdown(url: String): String {
        val response = client.get(url) {
            header(HttpHeaders.Accept, "text/markdown, text/html;q=0.9, */*;q=0.8")
        }
        val contentType = response.contentType()
        val body = response.bodyAsText()

        if (contentType?.match(ContentType("text", "markdown")) == true) {
            return body
        }

        val doc = Jsoup.parse(body)

        // Remove navigation, header, and footer
        doc.select("nav, header, footer, .docs-navigation, .site-header, .site-footer, .secondary-navigation, .docs-sidebar, .top-nav, .sub-nav, .bottom-nav").remove()

        // Get the main content if possible
        val mainContent = doc.selectFirst("main, .main-content, #content, .chapter") ?: doc.body()

        val options = MutableDataSet()
        val converter = FlexmarkHtmlConverter.builder(options).build()
        return converter.convert(mainContent.html())
    }

    override fun close() {
        client.close()
    }
}
