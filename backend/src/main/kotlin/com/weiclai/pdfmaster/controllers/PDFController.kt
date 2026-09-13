package com.weiclai.pdfmaster.controllers

import com.weiclai.pdfmaster.pdf.FileType
import com.weiclai.pdfmaster.pdf.PdfService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/pdf")
class PDFController(
    private val pdfService: PdfService,
) {
    @PostMapping("/combine", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload pdf files to combine")
    fun combinePdf(
        @RequestParam(value = "files", required = true) files: List<MultipartFile>,
    ): ResponseEntity<ByteArrayResource> = attachment("combined.pdf", pdfService.combine(files), MediaType.APPLICATION_PDF)

    @PostMapping("/add-watermark", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Add water mark to pdf")
    fun addWaterMark(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "watermarkText", defaultValue = "Testing") watermarkText: String = "Testing",
        @RequestParam(value = "fontSize", defaultValue = "45f") fontSize: Float = 45f,
        @RequestParam(value = "rotation", defaultValue = "45f") rotation: Float = 45f,
    ): ResponseEntity<ByteArrayResource> =
        attachment(
            "watermarked.pdf",
            pdfService.watermark(file, watermarkText, fontSize, rotation),
            MediaType.APPLICATION_PDF,
        )

    @PostMapping("/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Convert pdf to Word document (DOCX) or image. The format will not be retain and will only show text.")
    fun convertPdf(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "fileType", defaultValue = "DOCX") fileType: FileType,
    ): ResponseEntity<ByteArrayResource> =
        attachment(
            fileType.filename,
            pdfService.convert(file, fileType),
            MediaType.parseMediaType(fileType.mediaType),
        )

    private fun attachment(
        filename: String,
        bytes: ByteArray,
        mediaType: MediaType,
    ): ResponseEntity<ByteArrayResource> {
        val resource = ByteArrayResource(bytes)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .contentLength(resource.contentLength())
            .contentType(mediaType)
            .body(resource)
    }
}
