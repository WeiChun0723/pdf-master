package com.weiclai.pdfmaster

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SupabaseAuthConfigController(
    @param:Value("\${supabase.auth.url}") private val url: String,
    @param:Value("\${supabase.auth.publishable-key}") private val publishableKey: String,
) {
    @GetMapping("/auth/config")
    fun config() = mapOf("url" to url, "publishableKey" to publishableKey)
}
