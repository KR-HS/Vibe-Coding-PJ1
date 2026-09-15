package com.example.board.exception;

public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException(Long id) {
        super("첨부파일을 찾을 수 없습니다: " + id);
    }
}
