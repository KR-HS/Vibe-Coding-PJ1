package com.example.board.dto.response;

import java.time.LocalDateTime;

import com.example.board.entity.Comment;

public record MyCommentResponse(
        Long id,
        String content,
        Long boardId,
        String boardTitle,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MyCommentResponse from(Comment comment) {
        return new MyCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getBoard().getId(),
                comment.getBoard().getTitle(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
