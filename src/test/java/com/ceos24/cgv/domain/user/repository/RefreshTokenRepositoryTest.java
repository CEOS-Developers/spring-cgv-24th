package com.ceos24.cgv.domain.user.repository;

import com.ceos24.cgv.domain.user.entity.RefreshToken;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("해시로 조회하면 주인 사용자까지 한 번에 가져온다")
    void 해시로_조회하면_사용자까지_가져온다() {
        User user = persistUser("tokenuser");
        refreshTokenRepository.save(token(user, "a".repeat(64)));
        em.flush();
        em.clear();

        RefreshToken found = refreshTokenRepository.findWithUserByTokenHash("a".repeat(64)).orElseThrow();

        assertThat(found.getUser().getLoginId()).isEqualTo("tokenuser");
        assertThat(refreshTokenRepository.findByTokenHash("b".repeat(64))).isEmpty();
    }

    // 해시가 겹치면 조회 결과가 둘이 되어 어느 사용자의 토큰인지 정할 수 없다.
    @Test
    @DisplayName("같은 해시는 두 번 저장할 수 없다")
    void 같은_해시는_두_번_저장할_수_없다() {
        User user = persistUser("tokenuser");
        refreshTokenRepository.saveAndFlush(token(user, "a".repeat(64)));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(token(user, "a".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String loginId) {
        User user = TestFixtures.user(loginId);
        em.persist(user);
        return user;
    }

    private RefreshToken token(User user, String hash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.of(2030, 1, 1, 0, 0))
                .build();
    }
}
