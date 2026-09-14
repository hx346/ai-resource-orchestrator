package com.company.orchestrator.skill.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File text extraction: txt round-trip, docx unzip+strip, pdf via PDFBox. */
class DocumentTextExtractorTest {

    @Test
    void plainTextRoundTrip() {
        var text = DocumentTextExtractor.extract("resume.txt", "精通 Java 与 Spring Boot".getBytes(StandardCharsets.UTF_8));
        assertTrue(text.contains("Java"));
    }

    @Test
    void docxUnzipsAndStripsMarkup() throws IOException {
        var xml = "<w:document><w:body><w:p>精通 Java</w:p><w:p>熟悉 Python</w:p></w:body></w:document>";
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var text = DocumentTextExtractor.extract("resume.docx", out.toByteArray());
        assertTrue(text.contains("精通 Java"));
        assertTrue(text.contains("熟悉 Python"));
        assertTrue(text.contains("\n"));
    }

    @Test
    void pdfTextIsExtracted() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A6);
            document.addPage(page);
            PDFont helvetica = new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(helvetica, 12);
                stream.newLineAtOffset(15, 300);
                stream.showText("Proficient in Java and PDF parsing");
                stream.endText();
            }
            document.save(out);
        }
        var text = DocumentTextExtractor.extract("resume.pdf", out.toByteArray());
        assertTrue(text.contains("Java"));
    }

    @Test
    void brokenInputFailsWithTypedMessage() {
        assertThrows(IllegalArgumentException.class, () -> DocumentTextExtractor.extract("resume.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)));
    }
}
