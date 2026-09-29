package com.example.board.event;

public record CommentCreatedEvent(
        Long commentId,
        Long boardId,
        Long boardAuthorId,
        Long commenterId
) {
}
