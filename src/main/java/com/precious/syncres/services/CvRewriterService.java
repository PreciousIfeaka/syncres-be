package com.precious.syncres.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.precious.syncres.matcher.GeminiClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CvRewriterService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are a professional CV writer specializing in technical roles.
            Your task is to retailor a candidate's CV to better align with a specific Job Description (JD).
            
            CRITICAL CONSTRAINTS:
            1. NEVER invent experience, qualifications, skills, company names, dates, or job titles the candidate does not have.
            2. MAY: reorder bullet points, strengthen action verbs, incorporate relevant JD keywords the candidate demonstrably possesses, reframe existing experience in role-relevant language, expand the professional summary.
            3. NEVER modify URLs, email addresses, or any links found in the CV. Preserve them exactly as they appear in the original text.
            
            Return ONLY valid JSON with this exact schema:
            {
              "name": <string — unchanged, required>,
              "email": <string — unchanged, required>,
              "professional_summary": <string, required>,
              "experience": [
                {
                  "company": <string — unchanged>,
                  "title": <string — unchanged>,
                  "dates": <string — unchanged>,
                  "bullets": [<string>, ...]
                }
              ],
              "education": [
                {
                  "institution": <string — unchanged>,
                  "degree":      <string — unchanged>,
                  "year":        <string — unchanged>
                }
              ],
              "phone":           <string — unchanged, omit if not present>,
              "location":        <string — unchanged, omit if not present>,
              "linkedin":        <string — unchanged, omit if not present>,
              "github":          <string — unchanged, omit if not present>,
              "website":         <string — unchanged, omit if not present>,
              "technical_skills": [<string>, ... omit if not present],
              "soft_skills":      [<string>, ... omit if not present],
              "projects": [
                {
                  "name":    <string — unchanged, omit section if not present>,
                  "url":     <string — unchanged, omit if not present>,
                  "bullets": [<string>, ...]
                }
              ],
              "certifications": [<string>, ... omit if not present],
              "changes": [<string>, ...]
            }
            """;

    public RewriteResult rewrite(String cvText, String jdText, String gapSummary) {
        String userMessage = String.format("## Original CV\n%s\n\n## Job Description\n%s\n\n## Missing/Weak Skills\n%s", cvText, jdText, gapSummary);
        
        try {
            String jsonResponse = geminiClient.generate(SYSTEM_PROMPT, userMessage);
            return objectMapper.readValue(jsonResponse, RewriteResult.class);
        } catch (Exception e) {
            log.error("Failed to rewrite CV using Gemini", e);
            throw new RuntimeException("AI Rewriter failed: " + e.getMessage());
        }
    }

    @Data
    public static class RewriteResult {
        private String name;
        private String email;

        @JsonProperty("professional_summary")
        private String professionalSummary;

        private List<Experience> experience;
        private List<Education> education;
        private String phone;
        private String location;
        private String linkedin;
        private String github;
        private String website;

        @JsonProperty("technical_skills")
        private List<String> technicalSkills;

        @JsonProperty("soft_skills")
        private List<String> softSkills;

        private List<Project> projects;
        private List<String> certifications;
        private List<String> changes;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Experience {
            private String company;
            private String title;
            private String dates;
            private List<String> bullets;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Project {
            private String name;
            private String url;
            private List<String> bullets;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Education {
            private String institution;
            private String degree;
            private String year;
        }
    }
}
