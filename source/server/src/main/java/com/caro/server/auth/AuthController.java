package com.caro.server.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.user.User;
import com.caro.server.user.UserInfo;
import com.caro.server.user.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(String username, String password, String displayName) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, UserInfo user) {
    }

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserInfo register(@RequestBody RegisterRequest req) {
        return UserInfo.from(authService.register(req.username(), req.password(), req.displayName()));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        User user = authService.login(req.username(), req.password());
        return new LoginResponse(authService.issueToken(user), UserInfo.from(user));
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal AuthenticatedUser me) {
        return users.findById(me.id())
                .map(UserInfo::from)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản không tồn tại"));
    }
}
