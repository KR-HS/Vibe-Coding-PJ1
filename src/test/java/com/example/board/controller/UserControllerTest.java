package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.response.UserResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.security.CustomUserDetails;

class UserControllerTest {

    private final UserController userController = new UserController();

    @Test
    void 인증된_사용자는_내_정보를_조회할_수_있다() {
        User user = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        ResponseEntity<UserResponse> response = userController.me(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(1L);
        assertThat(response.getBody().email()).isEqualTo("user@example.com");
        assertThat(response.getBody().role()).isEqualTo(Role.USER);
    }
}
