package com.weiclai.pdfmaster

import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AuthenticatedUserResponse(
    val subject: String,
    val email: String?,
    val role: String?,
    val issuer: String,
    val expiresAt: Instant?,
)

@RestController
@RequestMapping("/api/auth")
class AuthenticatedUserController {
    @GetMapping("/me")
    @Operation(summary = "Show the authenticated user's validated JWT metadata")
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ) = AuthenticatedUserResponse(
        subject = jwt.subject,
        email = jwt.getClaimAsString("email"),
        role = jwt.getClaimAsString("role"),
        issuer = jwt.issuer.toString(),
        expiresAt = jwt.expiresAt,
    )
}
