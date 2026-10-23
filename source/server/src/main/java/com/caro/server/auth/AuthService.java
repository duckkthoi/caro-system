package com.caro.server.auth;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.user.Role;
import com.caro.server.user.User;
import com.caro.server.user.UserRepository;

/** Account logic shared by the REST API and the socket protocol. */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,20}$");
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 72; // BCrypt only uses the first 72 bytes
    private static final int DISPLAY_NAME_MAX = 30;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(String rawUsername, String password, String rawDisplayName) {
        String username = normalizeUsername(rawUsername);
        if (!USERNAME.matcher(username).matches()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Username gồm 3-20 ký tự a-z, 0-9 hoặc _");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mật khẩu phải dài từ 6 đến 72 ký tự");
        }
        String displayName = rawDisplayName == null || rawDisplayName.isBlank() ? username : rawDisplayName.trim();
        if (displayName.length() > DISPLAY_NAME_MAX) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên hiển thị tối đa 30 ký tự");
        }
        if (users.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_TAKEN, "Username đã tồn tại");
        }
        try {
            return users.saveAndFlush(new User(username, passwordEncoder.encode(password), displayName, Role.PLAYER));
        } catch (DataIntegrityViolationException e) {
            // two registrations with the same username raced past the exists check
            throw new AppException(ErrorCode.USERNAME_TAKEN, "Username đã tồn tại");
        }
    }

    @Transactional(readOnly = true)
    public User login(String rawUsername, String password) {
        User user = users.findByUsername(normalizeUsername(rawUsername))
                .filter(u -> password != null && passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS, "Sai username hoặc mật khẩu"));
        return requireActive(user);
    }

    /** Verifies a JWT and reloads the user, so a locked account is rejected even with a valid token. */
    @Transactional(readOnly = true)
    public User authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Thiếu token");
        }
        long userId = jwtService.verify(token);
        User user = users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản không tồn tại"));
        return requireActive(user);
    }

    public String issueToken(User user) {
        return jwtService.issue(user);
    }

    private static User requireActive(User user) {
        if (user.isLocked()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đã bị khóa");
        }
        return user;
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
