package com.caro.server.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.caro.server.config.CaroProperties;

/** Creates the admin account on first start (credentials from caro.admin.*). */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CaroProperties props;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, CaroProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = props.admin().username();
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new User(username, passwordEncoder.encode(props.admin().password()), "Administrator", Role.ADMIN));
        log.info("Created admin account '{}'", username);
    }
}
