package com.example.board.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.board.entity.SmsNotificationLog;

public interface SmsNotificationLogRepository extends JpaRepository<SmsNotificationLog, Long> {

    Page<SmsNotificationLog> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);
}
