package com.precious.syncres.services;

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
public class MatchScorerService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are an expert Applicant Tracking System (ATS) and Technical Recruiter.
            Your task is to analyze a candidate's CV against a Job Description (JD).
            
            Return ONLY valid JSON, no markdown, no preamble.
            JSON schema:
            {
              "overall_score": <integer 0-100>,
              "summary": <string, 2-3 sentences>,
              "matched_skills": [<string>, ...],
              "missing_skills": [<string>, ...],
              "weak_matches": [
                { "skill": <string>, "note": <string, one sentence> }
              ],
              "recommendation": <string, one sentence>
            }
            
            Scoring Guide: 80-100 excellent, 65-79 good, 50-64 moderate, 0-49 poor.
            Do not inflate scores. Judge on: skills alignment, experience level, domain knowledge, role seniority requirements.
            """;

    public MatchResult score(String cvText, String jdText) {
        String userMessage = String.format("## Candidate CV\n%s\n\n## Job Description\n%s\n\nAnalyse the match and respond with the JSON object as specified.", cvText, jdText);
        
        try {
            String jsonResponse = geminiClient.generate(SYSTEM_PROMPT, userMessage);
            return objectMapper.readValue(jsonResponse, MatchResult.class);
        } catch (Exception e) {
            log.error("Failed to score match using Gemini", e);
            throw new RuntimeException("AI Scorer failed: " + e.getMessage());
        }
    }

    @Data
    public static class MatchResult {
        @JsonProperty("overall_score")
        private int overallScore;
        private String summary;
        @JsonProperty("matched_skills")
        private List<String> matchedSkills;
        @JsonProperty("missing_skills")
        private List<String> missingSkills;
        @JsonProperty("weak_matches")
        private List<WeakMatch> weakMatches;
        private String recommendation;

        @Data
        public static class WeakMatch {
            private String skill;
            private String note;
        }
    }
}
