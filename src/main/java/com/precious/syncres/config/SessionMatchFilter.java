package com.precious.syncres.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SessionMatchFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        boolean isMatchEndpoint = path.equals("/api/match") || path.startsWith("/api/match/jobs/");
        
        if (isMatchEndpoint && SecurityContextHolder.getContext().getAuthentication() == null) {
            request.getSession(true);
        }

        filterChain.doFilter(request, response);
    }
}
