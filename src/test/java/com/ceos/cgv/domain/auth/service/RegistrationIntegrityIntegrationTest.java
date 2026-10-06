package com.ceos.cgv.domain.auth.service;

import com.ceos.cgv.domain.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationIntegrityIntegrationTest {
    @Autowired
    RegistrationService registrationService;

    @Test
    void 중복키가_아닌_DB_무결성_오류는_중복_가입_409로_숨기지_않는다() {
        SignupRequest invalid = new SignupRequest(
                "nullnameuser", null, "nullname@example.com", "Password123!");

        assertThatThrownBy(() -> registrationService.register(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
