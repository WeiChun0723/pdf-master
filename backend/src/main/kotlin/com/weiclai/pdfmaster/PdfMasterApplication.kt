package com.weiclai.pdfmaster

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PdfMasterApplication

fun main(args: Array<String>) {
    runApplication<PdfMasterApplication>(*args)
}
