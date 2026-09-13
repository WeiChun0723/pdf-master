package com.weiclai.pdfmaster.pdf

enum class FileType(
    val filename: String,
    val mediaType: String,
) {
    DOCX("word.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PNG("images.zip", "application/zip"),
}
