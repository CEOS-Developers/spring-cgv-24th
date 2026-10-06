package com.ceos24.spring_cgv.domain.auth.dto.response;

import com.ceos24.spring_cgv.domain.auth.enums.TokenType;
import com.ceos24.spring_cgv.domain.member.entity.Member;
import lombok.Builder;

@Builder
public record LoginResponse(

        AtInfo atInfo,
        MemberInfo memberInfo
){

    public static LoginResponse of(AtInfo atInfo, MemberInfo memberInfo){
        return LoginResponse.builder()
                .atInfo(atInfo)
                .memberInfo(memberInfo).build();
    }

    @Builder
    public record AtInfo(
            String accessToken,
            TokenType tokenType,
            Long expiresIn
    ){
        public static AtInfo of(String accessToken, TokenType tokenType, Long expiresIn){
            return AtInfo.builder()
                    .accessToken(accessToken)
                    .tokenType(tokenType)
                    .expiresIn(expiresIn).build();
        }
    }

    @Builder
    public record MemberInfo(
            Long memberId,
            String name,
            String email
    ){
        public static MemberInfo from(Member member){
            return MemberInfo.builder()
                    .memberId(member.getId())
                    .name(member.getName())
                    .email(member.getEmail())
                    .build();
        }
    }
}
