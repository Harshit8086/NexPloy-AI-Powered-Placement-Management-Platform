package com.nexploy.campuslink.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsPlainText() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain",
            "Backend engineer skilled in Java and Spring Boot.".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extract(file)).contains("Java", "Spring Boot");
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] pdf = pdfWithText("Skilled in Java and PostgreSQL. B.Tech in CSE.");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdf);

        assertThat(extractor.extract(file)).contains("Java", "PostgreSQL", "B.Tech");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> extractor.extract(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> extractor.extract(file))
            .isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    void truncatesToTheTextLengthCap() {
        String huge = "Java ".repeat(6000); // 30,000 chars, over the 20,000 cap
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", huge.getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extract(file)).hasSize(20_000);
    }

    private byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
