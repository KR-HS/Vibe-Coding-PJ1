package com.example.board.dto.response;

import java.time.LocalDateTime;

import com.example.board.entity.SmsNotificationLog;
import com.example.board.entity.SmsStatus;

public record SmsNotificationLogResponse(
        Long id,
        String message,
        SmsStatus status,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static SmsNotificationLogResponse from(SmsNotificationLog log) {
        return new SmsNotificationLogResponse(
                log.getId(), log.getMessage(), log.getStatus(), log.getErrorMessage(), log.getCreatedAt());
    }
}
