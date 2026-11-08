package com.ceos24.springboot.auth;

import com.ceos24.springboot.auth.dto.SignupRequest;
import com.ceos24.springboot.auth.service.SignupService;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SignupConstraintIntegrationTest {

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private SignupService signupService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void duplicateInsertedAfterPrecheckIsTranslatedToDuplicateEmail() {
        String email = "concurrent-signup@example.com";
        String password = "Test1234!";
        userRepository.saveAndFlush(User.create(
                "기존회원", email, passwordEncoder.encode(password), "010-1234-5678"
        ));

        // Simulate another transaction inserting the email after the precheck.
        // The insert and UNIQUE constraint violation still use the real H2 database.
        doReturn(false).when(userRepository).existsByEmail(email);

        SignupRequest request = new SignupRequest(
                "중복회원", email, password, "010-1234-5678"
        );

        assertThatThrownBy(() -> signupService.signupUser(request))
                .isInstanceOfSatisfying(GlobalException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DUPLICATE_EMAIL)
                );
    }
}
