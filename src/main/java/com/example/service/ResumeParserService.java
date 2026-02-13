package com.example.service;

import com.example.dto.ParsedResume;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ResumeParserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public ParsedResume parse(String text) {

        String email = extractEmail(text);
        String name = extractName(text);

        return new ParsedResume(name, email);
    }

    private String extractEmail(String text) {
        var matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "unknown@email.com";
    }

    private String extractName(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> line.length() > 3)
                .findFirst()
                .orElse("UNKNOWN");
    }
}
