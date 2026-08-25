package com.weiclai.pdfmaster

import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import com.weiclai.pdfmaster.controllers.PDFController
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

    @Test
    fun pdfApiRequiresCsrfAndAuthentication() {
        mockMvc
            .perform(post("/api/pdf/upload").contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isForbidden)

        val csrfCookie =
            mockMvc
                .perform(get("/auth/session"))
                .andReturn()
                .response
                .getCookie("XSRF-TOKEN")!!
        mockMvc
            .perform(
                post("/api/pdf/upload")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.value)
                    .contentType(MediaType.MULTIPART_FORM_DATA),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun currentUserEndpointRequiresAuthentication() {
        mockMvc
            .perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
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
                options("/api/pdf/upload")
                    .header(HttpHeaders.ORIGIN, "https://attacker.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
            ).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun docxConversionReturnsADocxAttachment() {
        val pdf = ByteArrayOutputStream()
        Document().apply {
            PdfWriter.getInstance(this, pdf)
            open()
            add(Paragraph("PDF Master"))
            close()
        }

        val response =
            PDFController().convertPdf(
                MockMultipartFile("file", "source.pdf", MediaType.APPLICATION_PDF_VALUE, pdf.toByteArray()),
                PDFController.FileType.DOCX,
            )

        assertEquals("attachment; filename=word.docx", response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            response.headers.contentType.toString(),
        )
        XWPFDocument(ByteArrayInputStream(response.body!!.byteArray)).use { document ->
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
