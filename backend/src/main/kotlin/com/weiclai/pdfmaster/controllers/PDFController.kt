package com.weiclai.pdfmaster.controllers

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Element.ALIGN_CENTER
import com.itextpdf.text.Font
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfGState
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private val DOCX_MEDIA_TYPE =
    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")

@RestController
@RequestMapping("/api/pdf")
class PDFController {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload pdf file",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "500", description = "Not a file"),
        ],
    )
    fun uploadPdf(
        @RequestParam(value = "files", required = true) file: MultipartFile,
    ) = "The file name ${file.originalFilename}, size ${file.size} uploaded successfully."

    @PostMapping("/combine", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload pdf files to combine",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "500", description = "Not a file"),
        ],
    )
    fun combinePdf(
        @RequestParam(value = "files", required = true) files: List<MultipartFile>,
    ): ResponseEntity<ByteArrayResource> {
        val outputStream = ByteArrayOutputStream()
        val document = Document()
        val writer = PdfCopy(document, outputStream)
        document.open()
        for (file in files) {
            val reader = PdfReader(file.inputStream)
            try {
                for (page in 1..reader.numberOfPages) {
                    writer.addPage(writer.getImportedPage(reader, page))
                }
            } finally {
                reader.close()
            }
        }
        document.close()
        return download("combined.pdf", MediaType.APPLICATION_PDF, outputStream.toByteArray())
    }

    @PostMapping("/add-watermark", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Add water mark to pdf",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "500", description = "Not a file"),
        ],
    )
    fun addWaterMark(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "watermarkText", defaultValue = "Testing") watermarkText: String = "Testing",
        @RequestParam(value = "fontSize", defaultValue = "45f") fontSize: Float = 45f,
        @RequestParam(value = "rotation", defaultValue = "45f") rotation: Float = 45f,
    ): ResponseEntity<ByteArrayResource> {
        val reader = PdfReader(file.inputStream)
        val outputStream = ByteArrayOutputStream()
        val stamper = PdfStamper(reader, outputStream)
        val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
        val font = Font(Font.FontFamily.HELVETICA, fontSize, Font.NORMAL, BaseColor.LIGHT_GRAY)

        for (i in 1..reader.numberOfPages) {
            val pageSize = reader.getPageSize(i)
            val x = (pageSize.left + pageSize.right) / 2
            val y = (pageSize.top + pageSize.bottom) / 2
            val content = stamper.getOverContent(i)

            val pdfGState = PdfGState()
            pdfGState.setFillOpacity(0.2f)
            content.setGState(pdfGState)

            content.beginText()
            content.setFontAndSize(baseFont, font.size)
            content.showTextAligned(ALIGN_CENTER, watermarkText, x, y, rotation)
            content.endText()
        }
        stamper.close()
        reader.close()
        return download("watermarked.pdf", MediaType.APPLICATION_PDF, outputStream.toByteArray())
    }

    @PostMapping("/convert", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Convert pdf to Word document (DOCX) or image. The format will not be retain and will only show text.",
        responses = [
            ApiResponse(responseCode = "200", description = "Success"),
            ApiResponse(responseCode = "500", description = "Not a file"),
        ],
    )
    fun convertPdf(
        @RequestParam(value = "file", required = true) file: MultipartFile,
        @RequestParam(value = "fileType", defaultValue = "DOCX") fileType: FileType,
    ): ResponseEntity<ByteArrayResource> =
        when (fileType) {
            FileType.DOCX -> convertToWordDocument(file)
            FileType.PNG -> convertToPNG(file)
        }

    private fun convertToPNG(file: MultipartFile): ResponseEntity<ByteArrayResource> {
        val archive = ByteArrayOutputStream()
        Loader.loadPDF(file.bytes).use { pdf ->
            val renderer = PDFRenderer(pdf)
            GZIPOutputStream(archive).use { gzip ->
                ZipOutputStream(gzip).use { zip ->
                    repeat(pdf.numberOfPages) { page ->
                        zip.putNextEntry(ZipEntry("image-$page.png"))
                        ImageIO.write(renderer.renderImageWithDPI(page, 300f, ImageType.RGB), "png", zip)
                        zip.closeEntry()
                    }
                }
            }
        }
        return download("images.gz", MediaType.APPLICATION_OCTET_STREAM, archive.toByteArray(), "gzip")
    }

    private fun convertToWordDocument(file: MultipartFile): ResponseEntity<ByteArrayResource> =
        Loader.loadPDF(file.bytes).use { pdf ->
            XWPFDocument().use { wordDocument ->
                wordDocument.createParagraph().createRun().setText(PDFTextStripper().getText(pdf))
                val output = ByteArrayOutputStream()
                wordDocument.write(output)
                download("word.docx", DOCX_MEDIA_TYPE, output.toByteArray())
            }
        }

    private fun download(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        contentEncoding: String? = null,
    ): ResponseEntity<ByteArrayResource> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$filename")
            .apply { contentEncoding?.let { header(HttpHeaders.CONTENT_ENCODING, it) } }
            .contentLength(content.size.toLong())
            .contentType(contentType)
            .body(ByteArrayResource(content))

    enum class FileType {
        DOCX,
        PNG,
    }
}
