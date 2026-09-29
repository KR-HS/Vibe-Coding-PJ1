package com.example.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneNumberUpdateRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "국가 코드를 포함한 형식(예: +821012345678)으로 입력해주세요.")
        String phoneNumber
) {
}
