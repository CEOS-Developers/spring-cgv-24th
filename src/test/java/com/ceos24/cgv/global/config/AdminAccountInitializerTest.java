package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.enums.UserRole;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer(String loginId, String password) {
        return new AdminAccountInitializer(userRepository, passwordEncoder, loginId, password);
    }

    @Test
    void 관리자_계정이_없으면_인코딩된_비밀번호로_생성한다() {
        when(userRepository.existsByLoginId("admin")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");

        initializer("admin", "raw-password").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getLoginId()).isEqualTo("admin");
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 이미_있으면_생성하지_않는다() {
        when(userRepository.existsByLoginId("admin")).thenReturn(true);

        initializer("admin", "raw-password").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 아이디가_비어_있으면_생성하지_않는다() {
        initializer("", "raw-password").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 비밀번호가_비어_있으면_생성하지_않는다() {
        initializer("admin", " ").run(null);

        verify(userRepository, never()).save(any());
    }
}
