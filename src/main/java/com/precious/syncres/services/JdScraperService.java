package com.precious.syncres.services;

import com.precious.syncres.shared.exception.JdScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class JdScraperService {

    @Value("${app.matcher.max-jd-chars}")
    private int maxJdChars;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public String scrape(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .followRedirects(true)
                    .get();

            // v2: Remove noisy elements
            doc.select("script, style, nav, header, footer, iframe, noscript, aside").remove();
            doc.select("[aria-hidden=true]").remove();
            doc.select(".cookie-banner, .cookie-notice, .consent-banner").remove();

            // v2: Selection strategy
            String text = doc.select("main, article, [role=main], .job-description, #job-description, .description__text").text();
            if (text.isEmpty() || text.length() < 200) {
                text = doc.body().text();
            }

            return postProcess(text);
        } catch (IOException e) {
            log.error("Failed to scrape JD from URL: {}", url, e);
            throw new JdScrapeException("Failed to scrape job description. Please paste the JD text directly instead.");
        }
    }

    private String postProcess(String text) {
        if (text == null) return "";

        String processed = text.replaceAll("\\s+", " ").trim();

        if (processed.length() > maxJdChars) {
            log.warn("JD text truncated from {} to {} characters", processed.length(), maxJdChars);
            processed = processed.substring(0, maxJdChars);
        }

        return processed;
    }
}
