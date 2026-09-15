package com.example.board.dto.request;

import com.example.board.entity.BoardCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BoardCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotNull BoardCategory category
) {
}
