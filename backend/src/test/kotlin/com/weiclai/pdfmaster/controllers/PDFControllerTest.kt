package com.weiclai.pdfmaster.controllers

import com.weiclai.pdfmaster.pdf.PdfService
import com.weiclai.pdfmaster.web.ApiExceptionHandler
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@WebMvcTest(
    controllers = [PDFController::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@Import(PdfService::class, ApiExceptionHandler::class)
@AutoConfigureMockMvc(addFilters = false)
class PDFControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun combinePdfMergesPages() {
        val result =
            mockMvc
                .perform(
                    multipart("/api/pdf/combine")
                        .file(pdfPart("files", "a.pdf", pdfBytes("one")))
                        .file(pdfPart("files", "b.pdf", pdfBytes("two"))),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=combined.pdf"))
                .andReturn()

        Loader.loadPDF(result.response.contentAsByteArray).use { document ->
            assertEquals(2, document.numberOfPages)
        }
    }

    @Test
    fun combinePdfRejectsNonPdf() {
        mockMvc
            .perform(
                multipart("/api/pdf/combine")
                    .file(pdfPart("files", "a.pdf", pdfBytes("one")))
                    .file(MockMultipartFile("files", "b.txt", MediaType.TEXT_PLAIN_VALUE, "nope".toByteArray())),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun watermarkReturnsPdf() {
        val result =
            mockMvc
                .perform(
                    multipart("/api/pdf/add-watermark")
                        .file(pdfPart("file", "doc.pdf", pdfBytes("hello")))
                        .param("watermarkText", "CONFIDENTIAL"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=watermarked.pdf"))
                .andReturn()

        Loader.loadPDF(result.response.contentAsByteArray).use { document ->
            assertEquals(1, document.numberOfPages)
        }
    }

    @Test
    fun convertDocxUsesWordContentType() {
        mockMvc
            .perform(
                multipart("/api/pdf/convert")
                    .file(pdfPart("file", "doc.pdf", pdfBytes("hello word")))
                    .param("fileType", "DOCX"),
            ).andExpect(status().isOk)
            .andExpect(
                content().contentType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ),
            ).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=word.docx"))
    }

    @Test
    fun convertPngReturnsZip() {
        val result =
            mockMvc
                .perform(
                    multipart("/api/pdf/convert")
                        .file(pdfPart("file", "doc.pdf", pdfBytes("page")))
                        .param("fileType", "PNG"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=images.zip"))
                .andReturn()

        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zip ->
            val entry = zip.nextEntry
            assertEquals("image-0.png", entry?.name)
            assertTrue(zip.readBytes().isNotEmpty())
            assertEquals(null, zip.nextEntry)
        }
    }

    @Test
    fun convertRejectsEmptyFile() {
        mockMvc
            .perform(
                multipart("/api/pdf/convert")
                    .file(MockMultipartFile("file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE, ByteArray(0))),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun convertRejectsNonPdf() {
        mockMvc
            .perform(
                multipart("/api/pdf/convert")
                    .file(
                        MockMultipartFile(
                            "file",
                            "x.bin",
                            MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            "not-pdf".toByteArray(),
                        ),
                    ),
            ).andExpect(status().isBadRequest)
    }
}

@WebMvcTest(
    controllers = [PDFController::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@Import(PdfService::class, ApiExceptionHandler::class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = ["pdf.max-pages=1"])
class PDFControllerPageLimitTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun convertRejectsOverMaxPages() {
        mockMvc
            .perform(
                multipart("/api/pdf/convert")
                    .file(pdfPart("file", "doc.pdf", pdfBytes("page", pages = 2)))
                    .param("fileType", "DOCX"),
            ).andExpect(status().isBadRequest)
    }
}

private fun pdfPart(
    field: String,
    filename: String,
    bytes: ByteArray,
) = MockMultipartFile(field, filename, MediaType.APPLICATION_PDF_VALUE, bytes)

private fun pdfBytes(
    text: String,
    pages: Int = 1,
): ByteArray {
    PDDocument().use { document ->
        repeat(pages) {
            val page = PDPage(PDRectangle(200f, 200f))
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(20f, 100f)
                content.showText(text)
                content.endText()
            }
        }
        val out = ByteArrayOutputStream()
        document.save(out)
        return out.toByteArray()
    }
}
