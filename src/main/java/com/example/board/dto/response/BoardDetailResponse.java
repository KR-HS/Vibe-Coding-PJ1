package com.example.board.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;

public record BoardDetailResponse(
        Long id,
        String title,
        String content,
        BoardCategory category,
        Long authorId,
        String authorName,
        int viewCount,
        List<AttachmentResponse> attachments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BoardDetailResponse of(Board board, List<AttachmentResponse> attachments) {
        return new BoardDetailResponse(
                board.getId(),
                board.getTitle(),
                board.getContent(),
                board.getCategory(),
                board.getUser().getId(),
                board.getUser().getName(),
                board.getViewCount(),
                attachments,
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }
}
