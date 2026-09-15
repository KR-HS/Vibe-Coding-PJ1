package com.example.board.dto.response;

import java.time.LocalDateTime;

import com.example.board.entity.BoardCategory;

public record BoardListItemResponse(
        Long id,
        String title,
        BoardCategory category,
        String authorName,
        int viewCount,
        long commentCount,
        LocalDateTime createdAt
) {
}
