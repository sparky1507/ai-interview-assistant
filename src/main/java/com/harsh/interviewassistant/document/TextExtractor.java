package com.harsh.interviewassistant.document;

import com.harsh.interviewassistant.common.exception.BadRequestException;
import com.harsh.interviewassistant.common.exception.UnsupportedMediaTypeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns uploaded bytes into plain text.
 *
 * The file type is detected from the content (magic bytes), with the filename only as a hint, so a
 * renamed executable can't get in as "resume.pdf" and the client's Content-Type header is never trusted.
 */
@Slf4j
@Component
public class TextExtractor {

    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String TEXT = "text/plain";

    private static final Map<String, String> SUPPORTED = Map.of(PDF, "PDF", DOCX, "DOCX", TEXT, "TXT");

    /** Thread-safe; loads Tika's detectors once. */
    private final Tika tika = new Tika();

    public record ExtractedText(String text, String contentType) {
    }

    public ExtractedText extract(byte[] bytes, String filename) {
        String contentType = detect(bytes, filename);
        if (!SUPPORTED.containsKey(contentType)) {
            throw new UnsupportedMediaTypeException(
                    "Only PDF, DOCX and TXT files are supported (detected: " + contentType + ")");
        }

        String text;
        try {
            text = new TikaDocumentReader(new NamedByteArrayResource(bytes, filename)).get().stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"))
                    .strip();
        } catch (RuntimeException e) {
            // Corrupt, truncated or password-protected files: the client's problem, not a 500
            log.info("Text extraction failed for '{}' ({}): {}", filename, contentType, e.getMessage());
            throw new BadRequestException("The file could not be read. Is it corrupt or password-protected?");
        }

        if (text.isEmpty()) {
            throw new BadRequestException(
                    "No text could be extracted. Scanned (image-only) PDFs are not supported; upload a text-based PDF or DOCX.");
        }
        return new ExtractedText(text, contentType);
    }

    private String detect(byte[] bytes, String filename) {
        String detected = tika.detect(bytes, filename);
        int params = detected.indexOf(';'); // e.g. "text/plain; charset=UTF-8"
        return params < 0 ? detected : detected.substring(0, params).strip();
    }

    /** TikaDocumentReader records the resource's filename as metadata; a plain ByteArrayResource has none. */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
