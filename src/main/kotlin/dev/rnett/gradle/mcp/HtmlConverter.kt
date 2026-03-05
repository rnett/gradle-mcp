package dev.rnett.gradle.mcp

import org.jsoup.Jsoup

class HtmlConverter(private val markdownService: MarkdownService) {
    fun convert(html: String, kind: DocsKind): String {
        val doc = Jsoup.parse(html)
        val root = when (kind) {
            DocsKind.USERGUIDE, DocsKind.SAMPLES -> {
                val r = doc.selectFirst("main.main-content") ?: doc.selectFirst("main") ?: doc.body()
                r.select("nav.docs-navigation").remove()
                r.select(".docs-sidebar").remove()
                r.select(".secondary-navigation").remove()
                r.select(".page-steps").remove()
                r
            }

            DocsKind.DSL -> {
                val r = doc.selectFirst("main.main-content") ?: doc.selectFirst("main") ?: doc.body()
                r.select("nav.docs-navigation").remove()
                r.select(".sidebar").remove()
                r.select(".breadcrumbs").remove()
                r
            }

            DocsKind.KOTLIN_DSL -> {
                val r = doc.selectFirst("div#content") ?: doc.selectFirst("main") ?: doc.body()
                r.select(".breadcrumbs").remove()
                r.select(".footer").remove()
                r.select(".navigation-wrapper").remove()
                r
            }

            DocsKind.JAVADOC -> {
                val r = doc.selectFirst("main[role=main]") ?: doc.selectFirst("main") ?: doc.body()
                r.select(".navbar").remove()
                r.select(".sub-nav").remove()
                r
            }

            DocsKind.RELEASE_NOTES -> {
                val r = doc.selectFirst("div.container") ?: doc.selectFirst("main") ?: doc.body()
                r.select(".toc").remove()
                r.select(".navigation").remove()
                r
            }
        }

        // General cleanup for all kinds
        root.select("script, style, link, meta, wbr").remove()
        root.select(".edit-link").remove()

        return markdownService.convertHtml(root.html())
    }
}
