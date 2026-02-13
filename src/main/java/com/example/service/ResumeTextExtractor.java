package com.example.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeTextExtractor {

    private final org.apache.tika.Tika tika = new org.apache.tika.Tika();

    public String extractText(File file) {
        try {
//            System.out.println(tika.parseToString(file));
            return tika.parseToString(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract resume text", e);
        }
    }
}

