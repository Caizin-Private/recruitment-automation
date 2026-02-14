package com.example.ats.parser;

import com.example.ats.model.ParsedResume;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeParser {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("(\\d+(\\.\\d+)?)\\s*(years|yrs)");

    // Detect capitalized names like "John Smith"
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[A-Z][a-z]+(\\s+[A-Z][a-z]+){1,2}$");

    private static final List<String> SKILLS = List.of(
            "java","spring","aws","docker","kubernetes",
            "mysql","postgresql","redis","react",
            "node","python","microservices"
    );

    public ParsedResume parse(String text){

        String email = extractEmail(text);

        String name = extractName(text, email);

        List<String> skills = extractSkills(text);

        double experience = extractExperience(text);

        List<String> projects = extractProjects(text);

        String education = extractEducation(text);

        int wordCount = text.split("\\s+").length;

        return new ParsedResume(
                name,
                email,
                skills,
                experience,
                projects,
                education,
                wordCount
        );
    }

    // ========================
    // EMAIL EXTRACTION
    // ========================

    private String extractEmail(String text){

        Matcher matcher = EMAIL_PATTERN.matcher(text);

        return matcher.find()
                ? matcher.group()
                : "unknown@email.com";
    }

    // ========================
    // PRODUCTION NAME EXTRACTION
    // ========================

    private String extractName(String text, String email){

        // Strategy 1: Extract from email
        String emailName = extractNameFromEmail(email);

        if(emailName != null)
            return emailName;

        // Strategy 2: First valid resume line
        Optional<String> firstLine =
                text.lines()
                        .map(String::trim)
                        .filter(this::isValidNameLine)
                        .findFirst();

        if(firstLine.isPresent())
            return firstLine.get();

        // Strategy 3: Scan all lines for name pattern
        for(String line : text.split("\n")){

            line = line.trim();

            if(NAME_PATTERN.matcher(line).matches())
                return line;
        }

        return "UNKNOWN";
    }

    private String extractNameFromEmail(String email){

        if(email == null || email.equals("unknown@email.com"))
            return null;

        try{

            String username =
                    email.split("@")[0];

            username =
                    username.replaceAll("[^a-zA-Z]", " ");

            String[] parts =
                    username.split("\\s+");

            if(parts.length >= 2){

                StringBuilder name =
                        new StringBuilder();

                for(String part : parts){

                    if(part.length() > 1){

                        name.append(
                                        Character.toUpperCase(part.charAt(0)))
                                .append(part.substring(1).toLowerCase())
                                .append(" ");
                    }
                }

                return name.toString().trim();
            }

        }catch(Exception ignored){}

        return null;
    }

    private boolean isValidNameLine(String line){

        if(line.isEmpty()) return false;

        if(line.length() < 3 || line.length() > 50)
            return false;

        if(line.toLowerCase().contains("resume"))
            return false;

        if(line.contains("@"))
            return false;

        if(line.matches(".*\\d.*"))
            return false;

        if(line.split("\\s+").length < 2)
            return false;

        return true;
    }

    // ========================
    // SKILLS EXTRACTION
    // ========================

    private List<String> extractSkills(String text){

        text = text.toLowerCase();

        List<String> found = new ArrayList<>();

        for(String skill : SKILLS){

            if(text.contains(skill))
                found.add(skill);
        }

        return found;
    }

    // ========================
    // EXPERIENCE EXTRACTION
    // ========================

    private double extractExperience(String text){

        Matcher matcher =
                EXPERIENCE_PATTERN.matcher(text.toLowerCase());

        double max = 0;

        while(matcher.find()){

            double years =
                    Double.parseDouble(matcher.group(1));

            max = Math.max(max, years);
        }

        return max;
    }

    // ========================
    // PROJECT EXTRACTION
    // ========================

    private List<String> extractProjects(String text){

        List<String> projects =
                new ArrayList<>();

        text.lines()
                .filter(line ->
                        line.toLowerCase().contains("project"))
                .limit(5)
                .forEach(projects::add);

        return projects;
    }

    // ========================
    // EDUCATION EXTRACTION
    // ========================

    private String extractEducation(String text){

        for(String line : text.split("\n")){

            line = line.toLowerCase();

            if(line.contains("b.tech")
                    || line.contains("m.tech")
                    || line.contains("bachelor")
                    || line.contains("master"))
                return line;
        }

        return "UNKNOWN";
    }
}