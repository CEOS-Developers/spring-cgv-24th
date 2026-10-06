package com.spring_cgv_24th.domain.auth.service;

import com.spring_cgv_24th.domain.auth.token.RefreshTokenHasher;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.jwt.RefreshTokenClaims;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock clock;

    public String issue(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        String token = jwtProvider.createRefreshToken(memberId);
        member.replaceRefreshToken(refreshTokenHasher.hash(token));
        return token;
    }

    // 호출한 재발급 서비스의 쓰기 트랜잭션 안에서 사용하면 토큰 발급까지 잠금이 유지된다.
    public Member findMemberByValidToken(String token) {
        Member member = findMemberForUpdate(token);
        validateStoredToken(member, token);
        return member;
    }

    public void revoke(String token) {
        Member member = findMemberForUpdate(token);
        // JWT 검증을 통과했고 이미 폐기된 상태라면 반복 로그아웃을 성공으로 처리한다.
        if (member.getRefreshTokenHash() == null) {
            return;
        }
        validateStoredToken(member, token);
        member.revokeRefreshToken();
    }

    private Member findMemberForUpdate(String token) {
        RefreshTokenClaims claims = jwtProvider.parseRefreshToken(token);
        Member member = memberRepository.findByIdForUpdate(claims.memberId())
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_INVALID));
        // 회원 행 잠금을 기다리는 동안 만료될 수 있으므로 조회가 끝난 뒤 다시 확인한다.
        if (!claims.expiresAt().isAfter(clock.instant())) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return member;
    }

    private void validateStoredToken(Member member, String token) {
        if (!refreshTokenHasher.matches(token, member.getRefreshTokenHash())) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }
}
