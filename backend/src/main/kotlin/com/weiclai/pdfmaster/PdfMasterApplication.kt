package com.weiclai.pdfmaster

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PdfMasterApplication

private val logger = LoggerFactory.getLogger(PdfMasterApplication::class.java)

fun main(args: Array<String>) {
    logger.info("=== PDF Master v0.0.1 - LATEST BUILD ===")
    runApplication<PdfMasterApplication>(*args)
}
