package com.weiclai.pdfmaster

import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
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
