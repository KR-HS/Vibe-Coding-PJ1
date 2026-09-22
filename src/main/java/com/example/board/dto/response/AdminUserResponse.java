package com.example.board.dto.response;

import java.time.LocalDateTime;

import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;

public record AdminUserResponse(
        Long id,
        String email,
        String name,
        Role role,
        Provider provider,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getProvider(),
                user.getCreatedAt()
        );
    }
}
