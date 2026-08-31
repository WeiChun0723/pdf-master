package com.weiclai.pdfmaster.pdf

class PdfRequestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
