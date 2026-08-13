package com.example.board.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
