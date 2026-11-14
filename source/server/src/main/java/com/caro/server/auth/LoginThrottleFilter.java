package com.caro.server.auth;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorBody;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies {@link LoginThrottle} to POST /api/auth/login and /api/auth/register. Runs before Spring MVC, so it writes
 * the 429 answer itself. Not a @Component: it is only registered inside the security filter chain.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginThrottle throttle;
    private final ObjectMapper mapper;

    public LoginThrottleFilter(LoginThrottle throttle, ObjectMapper mapper) {
        this.throttle = throttle;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            throttle.check(ClientIp.resolve(request.getRemoteAddr(), request.getHeader(ClientIp.HEADER)));
        } catch (AppException e) {
            response.setStatus(e.code().httpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getOutputStream(), new ErrorBody(e.code(), e.getMessage()));
            return;
        }
        chain.doFilter(request, response);
    }
}
