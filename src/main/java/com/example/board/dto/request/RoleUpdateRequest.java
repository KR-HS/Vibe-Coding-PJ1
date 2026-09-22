package com.example.board.dto.request;

import com.example.board.entity.Role;

import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull Role role
) {
}
