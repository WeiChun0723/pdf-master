package com.weiclai.pdfmaster

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

private const val CONTENT_SECURITY_POLICY =
    "default-src 'self'; " +
        "base-uri 'none'; " +
        "connect-src 'self'; " +
        "font-src 'self'; " +
        "form-action 'self'; " +
        "frame-ancestors 'none'; " +
        "img-src 'self' data:; " +
        "object-src 'none'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'"

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        csrfRepository.setCookieCustomizer {
            it.secure(true).sameSite("Strict").path("/")
        }

        http
            .csrf {
                it
                    .csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/auth/**",
                        "/docs/**",
                        "/login.html",
                        "/openapi/**",
                        "/swagger-ui/**",
                        "/actuator/health",
                    ).permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll()
            }.oauth2ResourceServer {
                it
                    .bearerTokenResolver(cookieBearerTokenResolver())
                    .jwt(Customizer.withDefaults())
            }

        return http.build()
    }

    @Bean
    fun cookieBearerTokenResolver(): BearerTokenResolver =
        BearerTokenResolver { request ->
            if (request.servletPath.startsWith("/api/")) {
                request.cookies?.firstOrNull { it.name == ACCESS_TOKEN_COOKIE }?.value
            } else {
                null
            }
        }

    @Bean
    fun securityHeadersFilter(): FilterRegistrationBean<Filter> {
        val filter =
            Filter { request, response, chain ->
                (response as HttpServletResponse).apply {
                    setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                    setHeader("Referrer-Policy", "no-referrer")
                    setHeader("X-Content-Type-Options", "nosniff")
                    setHeader("X-Frame-Options", "DENY")
                }
                chain.doFilter(request, response)
            }
        return FilterRegistrationBean(filter).apply {
            order = Ordered.HIGHEST_PRECEDENCE
        }
    }
}
