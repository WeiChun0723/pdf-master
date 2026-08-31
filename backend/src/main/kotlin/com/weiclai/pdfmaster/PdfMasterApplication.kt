package com.weiclai.pdfmaster

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@SpringBootApplication
class PdfMasterApplication

private val logger = LoggerFactory.getLogger(PdfMasterApplication::class.java)

fun main(args: Array<String>) {
    logger.info("PDF Master starting")
    runApplication<PdfMasterApplication>(*args)
}

@Configuration
class CorsConfig(
    @param:Value("\${pdf.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private val allowedOrigins: String,
) {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                val origins =
                    allowedOrigins
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toTypedArray()
                if (origins.isEmpty()) {
                    return
                }
                registry
                    .addMapping("/api/**")
                    .allowedOrigins(*origins)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*")
            }
        }
}
