package com.weiclai.pdfmaster.controllers

import com.weiclai.pdfmaster.pdf.FileType
import com.weiclai.pdfmaster.pdf.PdfService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload pdf file",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "400", description = "Invalid or empty PDF"),
        ],
    )
    fun uploadPdf(
        @RequestParam(value = "files", required = true) file: MultipartFile,
    ): String {
        pdfService.validate(file)
        return "The file name ${file.originalFilename}, size ${file.size} uploaded successfully."
    }

    @PostMapping("/combine", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload pdf files to combine",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "400", description = "Invalid or empty PDF"),
        ],
    )
    fun combinePdf(
        @RequestParam(value = "files", required = true) files: List<MultipartFile>,
    ): ResponseEntity<ByteArrayResource> = pdfAttachment("combined.pdf", pdfService.combine(files))

    @PostMapping("/add-watermark", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Add water mark to pdf",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "400", description = "Invalid or empty PDF"),
        ],
    )
    fun addWaterMark(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "watermarkText", defaultValue = "Testing") watermarkText: String = "Testing",
        @RequestParam(value = "fontSize", defaultValue = "45f") fontSize: Float = 45f,
        @RequestParam(value = "rotation", defaultValue = "45f") rotation: Float = 45f,
    ): ResponseEntity<ByteArrayResource> = pdfAttachment("watermarked.pdf", pdfService.watermark(file, watermarkText, fontSize, rotation))

    @PostMapping("/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Convert pdf to Word document (DOCX) or image. The format will not be retain and will only show text.",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "400", description = "Invalid or empty PDF"),
        ],
    )
    fun convertPdf(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "fileType", defaultValue = "DOCX") fileType: FileType,
    ): ResponseEntity<ByteArrayResource> {
        val body = pdfService.convert(file, fileType)
        return when (fileType) {
            FileType.DOCX ->
                attachment("word.docx", body, DOCX_MEDIA_TYPE)
            FileType.PNG ->
                attachment("images.zip", body, ZIP_MEDIA_TYPE)
        }
    }

    private fun pdfAttachment(
        filename: String,
        bytes: ByteArray,
    ): ResponseEntity<ByteArrayResource> = attachment(filename, bytes, MediaType.APPLICATION_PDF)

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

    companion object {
        private val DOCX_MEDIA_TYPE =
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
        private val ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip")
    }
}
