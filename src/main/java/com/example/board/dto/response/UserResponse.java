package com.example.board.dto.response;

import com.example.board.entity.Role;
import com.example.board.entity.User;

public record UserResponse(
        Long id,
        String email,
        String name,
        Role role,
        String phoneNumber
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getPhoneNumber());
    }
}
