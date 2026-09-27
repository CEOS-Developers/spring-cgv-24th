package com.ceos24.spring_cgv.domain.auth.dto.response;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import lombok.Builder;

@Builder
public record LoginResponse(

        AtInfo atInfo,
        RtInfo rtInfo,
        MemberInfo memberInfo
){

    public static LoginResponse of(AtInfo atInfo, RtInfo rtInfo, MemberInfo memberInfo){
        return LoginResponse.builder()
                .atInfo(atInfo)
                .rtInfo(rtInfo)
                .memberInfo(memberInfo).build();
    }

    @Builder
    public record AtInfo(
            String accessToken,
            String tokenType,
            Long expiresIn
    ){
        public static AtInfo of(String accessToken, String tokenType, Long expiresIn){
            return AtInfo.builder()
                    .accessToken(accessToken)
                    .tokenType(tokenType)
                    .expiresIn(expiresIn).build();
        }
    }

    @Builder
    public record RtInfo(
            String refreshToken,
            String tokenType,
            Long expiresIn
    ){
        public static RtInfo of(String refreshToken, String tokenType, Long expiresIn){
            return RtInfo.builder()
                    .refreshToken(refreshToken)
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
