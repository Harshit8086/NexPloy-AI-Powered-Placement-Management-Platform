package com.nexploy.campuslink.ai.service;

import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns an uploaded resume file into plain text for AI extraction. Supports PDF (via Apache PDFBox)
 * and UTF-8 text. It only reads the file's bytes as data — it never executes or interprets content —
 * and the extracted text is still screened for prompt injection by {@code ExtractionService} before
 * any model sees it.
 *
 * <p>Bounds: rejects empty files and anything over 10 MB, and truncates the extracted text to the
 * same {@value #MAX_TEXT_LENGTH}-character limit the JSON extraction endpoint enforces, so the file
 * path cannot bypass that cap.
 */
@Component
public class ResumeTextExtractor {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 20_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Resume file exceeds the 10 MB limit");
        }

        String contentType = file.getContentType() == null
            ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String text;
        try {
            if (contentType.contains("pdf") || nameEndsWith(file, ".pdf")) {
                text = extractPdf(file.getBytes());
            } else if (contentType.startsWith("text/") || nameEndsWith(file, ".txt")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else {
                throw new UnsupportedContentTypeException(file.getContentType());
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the resume file", exception);
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No readable text found in the resume");
        }
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private boolean nameEndsWith(MultipartFile file, String extension) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(extension);
    }
}
