package com.iflytek.skillhub.auth.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ExpiredPublicSessionFilterTest {

    private final ExpiredPublicSessionFilter filter =
            new ExpiredPublicSessionFilter(new RouteSecurityPolicyRegistry());

    @Test
    void expiredSessionOnPublicRoute_shouldBeHiddenFromDownstreamSecurityFilters() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/api/v1/skills");
        request.setCookies(
                new Cookie("SESSION", "expired"),
                new Cookie("JSESSIONID", "expired-servlet"),
                new Cookie("locale", "zh"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest downstream = capturedRequest(chain);
        assertNull(downstream.getRequestedSessionId());
        assertArrayEquals(new String[]{"locale"},
                java.util.Arrays.stream(downstream.getCookies()).map(Cookie::getName).toArray(String[]::new));
    }

    @Test
    void expiredSessionOnProtectedMethod_shouldRemainVisibleForUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("POST", "/api/v1/skills");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, capturedRequest(chain));
        assertEquals("expired", request.getRequestedSessionId());
    }

    @Test
    void validSessionOnPublicRoute_shouldRemainUnchanged() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/api/v1/search");
        request.setRequestedSessionIdValid(true);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, capturedRequest(chain));
    }

    @Test
    void forwardedPrefix_shouldUseServletPathForPublicRouteDecision() throws Exception {
        MockHttpServletRequest request = expiredSessionRequest("GET", "/skillhub/api/v1/search");
        request.setContextPath("/skillhub");
        request.setServletPath("/api/v1/search");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(capturedRequest(chain).getRequestedSessionId());
    }

    private static MockHttpServletRequest expiredSessionRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setRequestedSessionId("expired");
        request.setRequestedSessionIdValid(false);
        return request;
    }

    private static HttpServletRequest capturedRequest(FilterChain chain) throws Exception {
        ArgumentCaptor<ServletRequest> requestCaptor = ArgumentCaptor.forClass(ServletRequest.class);
        ArgumentCaptor<ServletResponse> responseCaptor = ArgumentCaptor.forClass(ServletResponse.class);
        verify(chain).doFilter(requestCaptor.capture(), responseCaptor.capture());
        return (HttpServletRequest) requestCaptor.getValue();
    }
}
