package com.example.board.dto.response;

public record LikeResponse(
        long likeCount,
        boolean liked
) {
}
