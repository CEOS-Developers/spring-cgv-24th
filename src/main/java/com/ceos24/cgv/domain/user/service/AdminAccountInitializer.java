package com.ceos24.cgv.domain.user.service;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

// 로컬 개발용 관리자 계정. 운영에서 부팅 부수효과로 관리자가 생기면 안 되므로 local 프로필에서만 뜬다.
// 비밀번호는 환경변수로만 받고 여기서 해시한다. data.sql로 넣으면 해시가 Git에 남아 오프라인 대입 대상이 된다.
@Slf4j
@Component
@Profile("local")
@EnableConfigurationProperties(AdminAccountInitializer.AdminAccount.class)
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccount adminAccount;

    // ddl-auto가 create라 로컬은 부팅마다 비어 있지만, 설정이 바뀌어도 중복 생성되지 않게 한다.
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByLoginId(adminAccount.loginId())) {
            return;
        }
        userRepository.save(User.createAdmin(
                adminAccount.loginId(),
                passwordEncoder.encode(adminAccount.password()),
                "관리자",
                LocalDate.of(2000, 1, 1),
                adminAccount.loginId() + "@cgv.local",
                "01000000000"));
        log.info("관리자 계정 생성: {}", adminAccount.loginId());
    }

    // 값이 비면 바인딩 단계에서 기동이 실패한다. 빈 비밀번호의 관리자가 조용히 생기는 것보다 낫다.
    @Validated
    @ConfigurationProperties(prefix = "admin")
    public record AdminAccount(
            @NotBlank String loginId,
            @NotBlank String password
    ) {
    }
}
