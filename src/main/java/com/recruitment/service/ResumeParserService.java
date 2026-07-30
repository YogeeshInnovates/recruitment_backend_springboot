package com.recruitment.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ResumeParserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d{1,3}[\\s-]?)?(\\d{10}|\\d{3}[\\s.-]\\d{3}[\\s.-]\\d{4})");

    private static final String[] NAME_KEYWORDS = {
            "experience", "skills", "education", "summary", "objective",
            "profile", "about", "contact", "address", "phone", "email",
            "resume", "curriculum", "vitae", "linkedin", "github",
            "developer", "engineer", "manager", "designer", "analyst"
    };

    public ParsedResume parse(MultipartFile file) {
        try {
            String text;
            String filename = file.getOriginalFilename();
            String lowerFilename = filename != null ? filename.toLowerCase() : "";

            if (lowerFilename.endsWith(".pdf")) {
                text = extractPdfText(file);
            } else if (lowerFilename.endsWith(".txt")) {
                text = new String(file.getBytes());
            } else {
                text = new String(file.getBytes());
            }

            log.info("Extracted {} characters from resume: {}", text.length(), filename);

            String email = extractEmail(text);
            String phone = extractPhone(text);
            String name = extractName(text, filename);
            String experience = extractExperience(text);
            String skills = extractSkills(text);

            return ParsedResume.builder()
                    .rawText(text.length() > 5000 ? text.substring(0, 5000) : text)
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .experience(experience)
                    .skills(skills)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse resume: {}", e.getMessage());
            return ParsedResume.builder()
                    .rawText("Unable to parse resume text")
                    .name(extractNameFromFilename(file.getOriginalFilename()))
                    .email("")
                    .phone("")
                    .experience("Not specified")
                    .skills("Not specified")
                    .build();
        }
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        var doc = Loader.loadPDF(file.getBytes());
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();
        return text;
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().toLowerCase();
        }
        return "";
    }

    private String extractPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return "";
    }

    private String extractName(String text, String filename) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.length() > 60) continue;

            String lower = line.toLowerCase();
            boolean isKeyword = false;
            for (String kw : NAME_KEYWORDS) {
                if (lower.contains(kw)) {
                    isKeyword = true;
                    break;
                }
            }
            if (isKeyword) continue;

            String[] words = line.split("\\s+");
            if (words.length >= 2 && words.length <= 4) {
                boolean allCapitalized = true;
                for (String w : words) {
                    if (w.isEmpty()) continue;
                    if (!Character.isUpperCase(w.charAt(0))) {
                        allCapitalized = false;
                        break;
                    }
                }
                if (allCapitalized) {
                    return line;
                }
            }
        }
        return extractNameFromFilename(filename);
    }

    private String extractNameFromFilename(String filename) {
        if (filename == null) return "Candidate";
        String name = filename.replaceAll("(?i)\\.(pdf|doc|docx|txt)$", "");
        name = name.replaceAll("[_\\-]", " ").trim();
        return name.isEmpty() ? "Candidate" : name;
    }

    private String extractExperience(String text) {
        String lower = text.toLowerCase();

        Pattern yearPattern = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\s*(?:of)?\\s*(?:experience)?");
        Matcher matcher = yearPattern.matcher(lower);
        if (matcher.find()) {
            return matcher.group().trim();
        }

        if (lower.contains("fresher") || lower.contains("recent graduate") || lower.contains("entry level")) {
            return "Fresher";
        }

        return "Not specified";
    }

    private String extractSkills(String text) {
        String lower = text.toLowerCase();
        List<String> techSkills = new ArrayList<>();

        String[] knownSkills = {
                "java", "python", "javascript", "typescript", "react", "angular", "vue",
                "spring", "spring boot", "node", "node.js", "express", "django", "flask",
                "sql", "postgresql", "mysql", "mongodb", "redis", "docker", "kubernetes",
                "aws", "azure", "gcp", "git", "jenkins", "ci/cd", "rest", "rest api",
                "graphql", "microservices", "html", "css", "tailwind",
                "c++", "c#", "go", "rust", "ruby", "php", "swift", "kotlin",
                "machine learning", "ai", "deep learning",
                "tensorflow", "pytorch", "pandas", "numpy",
                "agile", "scrum", "jira", "linux", "bash"
        };

        for (String skill : knownSkills) {
            if (lower.contains(skill)) {
                techSkills.add(skill);
            }
        }

        return techSkills.isEmpty() ? "Not specified" : String.join(", ", techSkills);
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class ParsedResume {
        private String rawText;
        private String name;
        private String email;
        private String phone;
        private String experience;
        private String skills;
    }
}
