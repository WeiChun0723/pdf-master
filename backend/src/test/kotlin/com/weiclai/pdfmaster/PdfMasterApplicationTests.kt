package com.weiclai.pdfmaster

import com.weiclai.pdfmaster.pdf.FileType
import com.weiclai.pdfmaster.pdf.PdfService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@SpringBootTest(
    properties = [
        "SUPABASE_AUTH_URL=https://example.supabase.co",
        "SUPABASE_AUTH_PUBLISHABLE_KEY=sb_publishable_test",
    ],
)
@AutoConfigureMockMvc
class PdfMasterApplicationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var pdfService: PdfService

    @Test
    fun pdfApiRequiresCsrfAndAuthentication() {
        mockMvc
            .perform(post("/api/pdf/combine").contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isForbidden)

        val csrfCookie =
            mockMvc
                .perform(get("/auth/session"))
                .andReturn()
                .response
                .getCookie("XSRF-TOKEN")!!
        mockMvc
            .perform(
                post("/api/pdf/combine")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.value)
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun loginUsesPkceAndSecureHttpOnlyFlowCookie() {
        mockMvc
            .perform(get("/auth/login"))
            .andExpect(status().isFound)
            .andExpect(
                header().string(
                    HttpHeaders.LOCATION,
                    allOf(
                        startsWith("https://example.supabase.co/auth/v1/authorize?provider=google"),
                        containsString("code_challenge="),
                        containsString("code_challenge_method=s256"),
                        containsString("redirect_to=http://localhost:8080/auth/callback"),
                    ),
                ),
            ).andExpect(cookie().exists("__Host-pdf-master-oauth"))
            .andExpect(cookie().httpOnly("__Host-pdf-master-oauth", true))
            .andExpect(cookie().path("__Host-pdf-master-oauth", "/"))
            .andExpect(cookie().secure("__Host-pdf-master-oauth", true))
            .andExpect(header().string("Clear-Site-Data", "\"storage\""))
    }

    @Test
    fun missingSessionCreatesCsrfCookieWithoutExposingTokens() {
        mockMvc
            .perform(get("/auth/session"))
            .andExpect(status().isUnauthorized)
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
            .andExpect(cookie().secure("XSRF-TOKEN", true))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(
                header().string(
                    "Content-Security-Policy",
                    "default-src 'self'; base-uri 'none'; connect-src 'self'; font-src 'self'; form-action 'self'; frame-ancestors 'none'; img-src 'self' data:; object-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'",
                ),
            ).andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
    }

    @Test
    fun crossOriginApiRequestsAreNotAllowed() {
        mockMvc
            .perform(
                options("/api/pdf/combine")
                    .header(HttpHeaders.ORIGIN, "https://attacker.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
            ).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun docxConversionReturnsADocxAttachment() {
        val pdfBytes =
            PDDocument().use { document ->
                val page = PDPage(PDRectangle(200f, 200f))
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    content.newLineAtOffset(20f, 100f)
                    content.showText("PDF Master")
                    content.endText()
                }
                ByteArrayOutputStream().also { document.save(it) }.toByteArray()
            }

        val docx =
            pdfService.convert(
                MockMultipartFile("file", "source.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes),
                FileType.DOCX,
            )

        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            assertEquals(
                "PDF Master",
                document.paragraphs
                    .single()
                    .text
                    .trim(),
            )
        }
    }
}
