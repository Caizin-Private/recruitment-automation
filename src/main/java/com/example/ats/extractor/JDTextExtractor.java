package com.example.ats.extractor;

import com.example.service.ResumeTextExtractor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class JDTextExtractor {

    private final ResumeTextExtractor textExtractor;

    // Cache JD text so we don't re-read PDF every time
    private String cachedJDText;

    public JDTextExtractor(ResumeTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    /**
     * Returns Job Description text extracted from jd.pdf
     */
    public String getJDText() {

        // Return cached version if already loaded
        if (cachedJDText != null) {
            return cachedJDText;
        }

        try {

            // Load jd.pdf from resources folder
            File jdFile =
                    new ClassPathResource("jd.pdf").getFile();

            // Extract text using Apache Tika
            cachedJDText =
                    textExtractor.extractText(jdFile);

            if (cachedJDText == null || cachedJDText.isBlank()) {
                throw new RuntimeException(
                        "JD text extraction returned empty content");
            }

            return cachedJDText;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to load or extract JD PDF",
                    e
            );
        }
    }

    /**
     * Optional: force reload JD from disk
     */
    public void reloadJD() {
        cachedJDText = null;
    }
}