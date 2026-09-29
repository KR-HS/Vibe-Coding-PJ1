package com.example.board.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sms_notification_logs",
        indexes = @Index(name = "idx_sms_notification_logs_recipient", columnList = "recipient_user_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmsStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SmsNotificationLog(Long recipientUserId, String phoneNumber, String message, SmsStatus status,
            String errorMessage) {
        this.recipientUserId = recipientUserId;
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
