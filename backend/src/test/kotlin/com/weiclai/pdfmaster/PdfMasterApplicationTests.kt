package com.weiclai.pdfmaster

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
    fun contextLoads() {
    }

    @Test
    fun pdfApiRequiresAuthentication() {
        mockMvc
            .perform(post("/api/pdf/upload").contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun currentUserEndpointRequiresAuthentication() {
        mockMvc
            .perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
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
}
