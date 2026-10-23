package com.caro.server.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.config.CaroProperties;
import com.caro.server.user.User;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies HS256 JWTs. The subject is the user id. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(CaroProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = props.jwt().ttl();
    }

    public String issue(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** @return the user id stored in the token */
    public long verify(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Long.parseLong(subject);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn");
        }
    }
}
