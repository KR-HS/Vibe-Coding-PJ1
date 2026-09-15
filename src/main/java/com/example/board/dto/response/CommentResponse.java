package com.example.board.dto.response;

import java.time.LocalDateTime;

import com.example.board.entity.Comment;

public record CommentResponse(
        Long id,
        String content,
        Long authorId,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUser().getId(),
                comment.getUser().getName(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
