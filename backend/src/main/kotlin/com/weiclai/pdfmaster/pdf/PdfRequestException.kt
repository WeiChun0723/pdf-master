package com.weiclai.pdfmaster.pdf

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
class PdfRequestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
