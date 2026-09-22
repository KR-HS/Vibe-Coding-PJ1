package com.example.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.response.AdminUserResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.exception.UserNotFoundException;
import com.example.board.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminService adminService;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .email("admin@example.com")
                .name("관리자")
                .provider(Provider.LOCAL)
                .role(Role.ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);

        member = User.builder()
                .email("member@example.com")
                .name("회원")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 2L);
    }

    @Test
    void 키워드_없이_회원_목록을_조회한다() {
        given(userRepository.findAll(PageRequest.of(0, 10, Sort.by("createdAt").descending())))
                .willReturn(new PageImpl<>(List.of(admin, member)));

        PageResponse<AdminUserResponse> response = adminService.getUsers(null, 0, 10);

        assertThat(response.content()).hasSize(2);
    }

    @Test
    void 키워드로_회원_목록을_검색한다() {
        given(userRepository.findByEmailContainingOrNameContaining(
                "member", "member", PageRequest.of(0, 10, Sort.by("createdAt").descending())))
                .willReturn(new PageImpl<>(List.of(member)));

        PageResponse<AdminUserResponse> response = adminService.getUsers("member", 0, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).email()).isEqualTo("member@example.com");
    }

    @Test
    void 관리자가_다른_회원의_권한을_변경한다() {
        given(userRepository.findById(2L)).willReturn(Optional.of(member));

        adminService.changeRole(1L, 2L, Role.ADMIN);

        assertThat(member.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void 자기_자신의_권한을_변경하려하면_예외가_발생한다() {
        assertThatThrownBy(() -> adminService.changeRole(1L, 1L, Role.USER))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void 존재하지_않는_회원의_권한을_변경하면_예외가_발생한다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.changeRole(1L, 999L, Role.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
    }
}
