package com.example.board.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.board.entity.Role;

class JwtTokenProviderTest {

    private static final String SECRET = "L28UFAblAlbJlO6pBmYKkNsEPJ4uDtzSM9p4qSzkxsU=";

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(SECRET, 1800L, 1_209_600L);

    @Test
    void Access_Token을_생성하고_파싱하면_userId와_role을_확인할_수_있다() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, Role.USER);

        assertThat(jwtTokenProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(1L);
    }

    @Test
    void Refresh_Token을_생성하면_유효성_검증을_통과한다() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getUserId(refreshToken)).isEqualTo(1L);
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(SECRET, -1L, -1L);
        String expiredToken = expiredTokenProvider.generateAccessToken(1L, Role.USER);

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void 서명이_변조된_토큰은_검증에_실패한다() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, Role.USER);
        int tamperIndex = accessToken.length() / 2;
        char original = accessToken.charAt(tamperIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tamperedToken = accessToken.substring(0, tamperIndex)
                + replacement
                + accessToken.substring(tamperIndex + 1);

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    void 형식이_올바르지_않은_토큰은_검증에_실패한다() {
        assertThat(jwtTokenProvider.validateToken("not-a-valid-jwt")).isFalse();
    }
}
