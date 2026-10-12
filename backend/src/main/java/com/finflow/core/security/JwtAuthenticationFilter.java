package com.finflow.core.security;

import com.finflow.modules.auth.service.SessionData;
import com.finflow.modules.auth.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that authenticates requests using Bearer JWT tokens and validates against active Redis sessions.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String SESSION_ID_ATTRIBUTE = "SESSION_ID";
    public static final String SESSION_KEY_ATTRIBUTE = "SESSION_ENCRYPTION_KEY";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, SessionService sessionService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // Ensure this is not an intermediate MFA challenge token
            if (!jwtTokenProvider.isMfaChallengeToken(token)) {
                String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
                UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                String email = jwtTokenProvider.getEmailFromToken(token);
                List<String> roles = jwtTokenProvider.getRolesFromToken(token);

                if (sessionId != null) {
                    Optional<SessionData> sessionOpt = sessionService.getSession(sessionId);
                    if (sessionOpt.isPresent()) {
                        SessionData session = sessionOpt.get();
                        UserPrincipal principal = new UserPrincipal(userId, email, sessionId, roles);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        // Attach session attributes for controller / filter de-obfuscation convenience
                        request.setAttribute(SESSION_ID_ATTRIBUTE, sessionId);
                        request.setAttribute(SESSION_KEY_ATTRIBUTE, session.getSessionEncryptionKey());

                        // Update sliding activity timestamp asynchronously or lightweight
                        sessionService.updateActivity(sessionId);
                    } else {
                        log.debug("Session {} not found or expired in Redis", sessionId);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
