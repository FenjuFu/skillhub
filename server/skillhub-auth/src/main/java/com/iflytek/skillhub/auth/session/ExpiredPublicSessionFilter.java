package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Treats an expired session cookie as absent only for routes that already allow anonymous access.
 */
public final class ExpiredPublicSessionFilter extends OncePerRequestFilter {

    private static final Set<String> SESSION_COOKIES = Set.of("SESSION", "JSESSIONID");

    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public ExpiredPublicSessionFilter(RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestPath = RouteSecurityPolicyRegistry.requestPath(request);
        boolean publicRoute = routeSecurityPolicyRegistry.accessLevel(request.getMethod(), requestPath)
                == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL;
        if (publicRoute && request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
            filterChain.doFilter(new SessionlessRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class SessionlessRequest extends HttpServletRequestWrapper {

        private SessionlessRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public Cookie[] getCookies() {
            Cookie[] cookies = super.getCookies();
            if (cookies == null) {
                return null;
            }
            Cookie[] retained = Arrays.stream(cookies)
                    .filter(cookie -> !SESSION_COOKIES.contains(cookie.getName()))
                    .toArray(Cookie[]::new);
            return retained.length == 0 ? null : retained;
        }
    }
}
