package com.weiclai.pdfmaster

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
}
