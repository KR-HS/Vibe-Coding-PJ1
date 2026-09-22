package com.example.board.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.board.dto.response.AdminUserResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.exception.UserNotFoundException;
import com.example.board.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;

    public PageResponse<AdminUserResponse> getUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = (keyword == null || keyword.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingOrNameContaining(keyword, keyword, pageable);
        List<AdminUserResponse> content = result.getContent().stream()
                .map(AdminUserResponse::from)
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional
    public void changeRole(Long adminId, Long targetUserId, Role role) {
        if (adminId.equals(targetUserId)) {
            throw new ForbiddenOperationException("자기 자신의 권한은 변경할 수 없습니다.");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
        user.changeRole(role);
    }
}
