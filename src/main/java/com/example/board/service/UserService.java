package com.example.board.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.SmsNotificationLogResponse;
import com.example.board.entity.SmsNotificationLog;
import com.example.board.entity.User;
import com.example.board.exception.UserNotFoundException;
import com.example.board.repository.SmsNotificationLogRepository;
import com.example.board.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SmsNotificationLogRepository smsNotificationLogRepository;

    @Transactional
    public void updatePhoneNumber(Long userId, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.changePhoneNumber(phoneNumber);
    }

    public PageResponse<SmsNotificationLogResponse> getMyNotificationLogs(Long userId, int page, int size) {
        Page<SmsNotificationLog> result = smsNotificationLogRepository.findByRecipientUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return PageResponse.of(
                result.getContent().stream().map(SmsNotificationLogResponse::from).toList(),
                page, size, result.getTotalElements());
    }
}
