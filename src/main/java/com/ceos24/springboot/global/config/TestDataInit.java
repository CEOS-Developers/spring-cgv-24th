package com.ceos24.springboot.global.config;

import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestDataInit implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        String encodedPassword =
                passwordEncoder.encode("1234");

        User user = User.create(
                "테스트유저",
                "test@test.com",
                encodedPassword,
                "010-1234-5678"
        );

        userRepository.save(user);
    }
}