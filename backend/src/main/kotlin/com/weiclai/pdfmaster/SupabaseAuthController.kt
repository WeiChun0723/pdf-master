package com.weiclai.pdfmaster

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

internal const val ACCESS_TOKEN_COOKIE = "__Host-pdf-master-access"
private const val REFRESH_TOKEN_COOKIE = "__Host-pdf-master-refresh"
private const val OAUTH_FLOW_COOKIE = "__Host-pdf-master-oauth"
private val REFRESH_COOKIE_AGE: Duration = Duration.ofDays(30)
private val OAUTH_COOKIE_AGE: Duration = Duration.ofMinutes(10)

private data class SupabaseSession(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
)

@RestController
class SupabaseAuthController(
    @param:Value("\${supabase.auth.url}") private val supabaseUrl: String,
    @Value("\${supabase.auth.publishable-key}") publishableKey: String,
    @param:Value("\${supabase.auth.redirect-uri}") private val redirectUri: String,
    private val jwtDecoder: JwtDecoder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val supabase =
        RestClient
            .builder()
            .baseUrl("${supabaseUrl.trimEnd('/')}/auth/v1")
            .defaultHeader("apikey", publishableKey)
            .build()

    @GetMapping("/auth/login")
    fun login(response: HttpServletResponse) {
        val state = randomValue()
        val verifier = randomValue()
        val callback =
            UriComponentsBuilder
                .fromUriString(redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString()
        val authorizeUrl =
            UriComponentsBuilder
                .fromUriString("${supabaseUrl.trimEnd('/')}/auth/v1/authorize")
                .queryParam("provider", "google")
                .queryParam("redirect_to", callback)
                .queryParam("code_challenge", sha256(verifier))
                .queryParam("code_challenge_method", "s256")
                .build()
                .encode()
                .toUriString()

        noStore(response)
        response.setHeader("Clear-Site-Data", "\"storage\"")
        setCookie(response, OAUTH_FLOW_COOKIE, "$state.$verifier", OAUTH_COOKIE_AGE)
        response.sendRedirect(authorizeUrl)
    }

    @GetMapping("/auth/callback")
    fun callback(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
    ) {
        noStore(response)
        val flow = cookie(request, OAUTH_FLOW_COOKIE)?.split('.', limit = 2)
        clearCookie(response, OAUTH_FLOW_COOKIE)
        if (code == null || state == null || flow?.size != 2 || !sameValue(flow[0], state)) {
            response.sendRedirect("/login.html?error=invalid_login")
            return
        }

        try {
            val session = exchangeSession("pkce", mapOf("auth_code" to code, "code_verifier" to flow[1]))
            if (session == null) {
                log.warn("Supabase login exchange returned an empty session")
                response.sendRedirect("/login.html?error=login_failed")
                return
            }
            setSessionCookies(response, session)
            response.sendRedirect("/docs/index.html")
        } catch (exception: RestClientException) {
            log.warn("Supabase login exchange failed: {}", exception.javaClass.simpleName)
            response.sendRedirect("/login.html?error=login_failed")
        }
    }

    @GetMapping("/auth/session")
    fun session(
        request: HttpServletRequest,
        response: HttpServletResponse,
        csrfToken: CsrfToken,
    ) {
        noStore(response)
        csrfToken.token
        val accessToken = cookie(request, ACCESS_TOKEN_COOKIE)
        if (accessToken != null && validForAnotherMinute(accessToken)) {
            response.status = HttpStatus.NO_CONTENT.value()
            return
        }

        val refreshToken = cookie(request, REFRESH_TOKEN_COOKIE)
        if (refreshToken == null) {
            clearSessionCookies(response)
            response.status = HttpStatus.UNAUTHORIZED.value()
            return
        }

        try {
            val refreshed = exchangeSession("refresh_token", mapOf("refresh_token" to refreshToken))
            if (refreshed == null) {
                clearSessionCookies(response)
                response.status = HttpStatus.UNAUTHORIZED.value()
                return
            }
            setSessionCookies(response, refreshed)
            response.status = HttpStatus.NO_CONTENT.value()
        } catch (exception: RestClientException) {
            log.info("Supabase session refresh failed: {}", exception.javaClass.simpleName)
            clearSessionCookies(response)
            response.status = HttpStatus.UNAUTHORIZED.value()
        }
    }

    @PostMapping("/auth/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        noStore(response)
        cookie(request, ACCESS_TOKEN_COOKIE)?.let { accessToken ->
            try {
                supabase
                    .post()
                    .uri("/logout?scope=local")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .toBodilessEntity()
            } catch (exception: RestClientException) {
                log.info("Supabase logout failed: {}", exception.javaClass.simpleName)
            }
        }
        clearSessionCookies(response)
        response.status = HttpStatus.NO_CONTENT.value()
    }

    private fun validForAnotherMinute(accessToken: String): Boolean =
        runCatching {
            jwtDecoder.decode(accessToken).expiresAt?.isAfter(Instant.now().plusSeconds(60)) == true
        }.getOrDefault(false)

    private fun setSessionCookies(
        response: HttpServletResponse,
        session: SupabaseSession,
    ) {
        setCookie(response, ACCESS_TOKEN_COOKIE, session.access_token, Duration.ofSeconds(session.expires_in))
        setCookie(response, REFRESH_TOKEN_COOKIE, session.refresh_token, REFRESH_COOKIE_AGE)
    }

    private fun clearSessionCookies(response: HttpServletResponse) {
        clearCookie(response, ACCESS_TOKEN_COOKIE)
        clearCookie(response, REFRESH_TOKEN_COOKIE)
    }

    private fun exchangeSession(
        grantType: String,
        body: Map<String, String>,
    ): SupabaseSession? =
        supabase
            .post()
            .uri("/token?grant_type=$grantType")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(SupabaseSession::class.java)

    private fun setCookie(
        response: HttpServletResponse,
        name: String,
        value: String,
        maxAge: Duration,
    ) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString(),
        )
    }

    private fun clearCookie(
        response: HttpServletResponse,
        name: String,
    ) = setCookie(response, name, "", Duration.ZERO)

    private fun cookie(
        request: HttpServletRequest,
        name: String,
    ): String? = request.cookies?.firstOrNull { it.name == name }?.value

    private fun randomValue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.US_ASCII)))

    private fun sameValue(
        expected: String,
        actual: String,
    ): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            actual.toByteArray(StandardCharsets.US_ASCII),
        )

    private fun noStore(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
    }
}
