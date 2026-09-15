package com.example.board.dto.response;

import com.example.board.entity.Attachment;

public record AttachmentResponse(
        Long id,
        String originalFilename,
        long fileSize,
        String downloadUrl
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getFileSize(),
                "/api/boards/%d/attachments/%d".formatted(attachment.getBoard().getId(), attachment.getId())
        );
    }
}
