package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(name = "admin.seed.enabled", havingValue = "true")
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_NICKNAME = "관리자";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String loginId;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   @Value("${admin.login-id:}") String loginId,
                                   @Value("${admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginId = loginId;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(loginId) || !StringUtils.hasText(password)) {
            log.warn("관리자 계정 시드가 활성화되었지만 아이디 또는 비밀번호가 설정되지 않아 건너뜁니다.");
            return;
        }
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }
        userRepository.save(User.createAdmin(loginId, passwordEncoder.encode(password), ADMIN_NICKNAME, null));
        log.info("관리자 계정을 생성했습니다. loginId={}", loginId);
    }
}
