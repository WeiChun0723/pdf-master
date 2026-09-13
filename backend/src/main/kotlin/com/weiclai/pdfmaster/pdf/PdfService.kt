package com.weiclai.pdfmaster.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.util.Matrix
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

@Service
class PdfService(
    @param:Value("\${pdf.max-pages:100}") private val maxPages: Int,
    @param:Value("\${pdf.render-dpi:300}") private val renderDpi: Float,
) {
    private fun validate(file: MultipartFile) {
        if (file.isEmpty) {
            throw PdfRequestException("Empty file")
        }
        if (!file.bytes.copyOf(4).contentEquals("%PDF".toByteArray())) {
            throw PdfRequestException("Not a PDF")
        }
    }

    fun combine(files: List<MultipartFile>): ByteArray {
        if (files.isEmpty()) {
            throw PdfRequestException("No files")
        }
        val documents = mutableListOf<PDDocument>()
        try {
            for (file in files) {
                documents += loadValidated(file)
            }
            PDDocument().use { destination ->
                val merger = PDFMergerUtility()
                for (source in documents) {
                    merger.appendDocument(destination, source)
                }
                return ByteArrayOutputStream().use { out ->
                    destination.save(out)
                    out.toByteArray()
                }
            }
        } catch (ex: IOException) {
            throw PdfRequestException("Invalid PDF", ex)
        } finally {
            documents.forEach { document -> runCatching { document.close() } }
        }
    }

    fun watermark(
        file: MultipartFile,
        text: String,
        fontSize: Float,
        rotation: Float,
    ): ByteArray {
        loadValidated(file).use { document ->
            // ponytail: Helvetica, embed a TTF if watermark text needs non-Latin glyphs
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val radians = Math.toRadians(rotation.toDouble())
            for (page in document.pages) {
                PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { content ->
                    val graphicsState = PDExtendedGraphicsState()
                    graphicsState.nonStrokingAlphaConstant = 0.2f
                    content.setGraphicsStateParameters(graphicsState)
                    content.setNonStrokingColor(0.75f)
                    content.setFont(font, fontSize)

                    val box = page.mediaBox
                    val centerX = box.lowerLeftX + box.width / 2
                    val centerY = box.lowerLeftY + box.height / 2
                    val textWidth = font.getStringWidth(text) / 1000f * fontSize
                    val offsetX = -textWidth / 2f
                    val cos = cos(radians).toFloat()
                    val sin = sin(radians).toFloat()
                    val matrix =
                        Matrix(
                            cos,
                            sin,
                            -sin,
                            cos,
                            centerX + offsetX * cos,
                            centerY + offsetX * sin,
                        )

                    content.beginText()
                    content.setTextMatrix(matrix)
                    content.showText(text)
                    content.endText()
                }
            }
            return ByteArrayOutputStream().use { out ->
                document.save(out)
                out.toByteArray()
            }
        }
    }

    fun convert(
        file: MultipartFile,
        fileType: FileType,
    ): ByteArray =
        when (fileType) {
            FileType.DOCX -> convertToWordDocument(file)
            FileType.PNG -> convertToPngZip(file)
        }

    private fun convertToPngZip(file: MultipartFile): ByteArray {
        loadValidated(file).use { document ->
            val renderer = PDFRenderer(document)
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zipOut ->
                for (page in 0 until document.numberOfPages) {
                    val image = renderer.renderImageWithDPI(page, renderDpi, ImageType.RGB)
                    zipOut.putNextEntry(ZipEntry("image-$page.png"))
                    ImageIO.write(image, "png", zipOut)
                    zipOut.closeEntry()
                }
            }
            return zipBytes.toByteArray()
        }
    }

    private fun convertToWordDocument(file: MultipartFile): ByteArray {
        loadValidated(file).use { document ->
            val text = PDFTextStripper().getText(document)
            XWPFDocument().use { wordDocument ->
                wordDocument.createParagraph().createRun().setText(text)
                return ByteArrayOutputStream().use { output ->
                    wordDocument.write(output)
                    output.toByteArray()
                }
            }
        }
    }

    private fun loadValidated(file: MultipartFile): PDDocument {
        validate(file)
        val document =
            try {
                Loader.loadPDF(file.bytes)
            } catch (ex: IOException) {
                throw PdfRequestException("Invalid PDF", ex)
            }
        if (document.numberOfPages > maxPages) {
            runCatching { document.close() }
            throw PdfRequestException("PDF exceeds $maxPages pages")
        }
        return document
    }
}
