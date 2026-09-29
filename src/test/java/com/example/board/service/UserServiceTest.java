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
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.SmsNotificationLogResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.SmsNotificationLog;
import com.example.board.entity.SmsStatus;
import com.example.board.entity.User;
import com.example.board.exception.UserNotFoundException;
import com.example.board.repository.SmsNotificationLogRepository;
import com.example.board.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SmsNotificationLogRepository smsNotificationLogRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void 전화번호를_등록하면_사용자_정보에_반영된다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.updatePhoneNumber(1L, "+821012345678");

        assertThat(user.getPhoneNumber()).isEqualTo("+821012345678");
    }

    @Test
    void 존재하지_않는_사용자의_전화번호를_변경하면_예외가_발생한다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updatePhoneNumber(999L, "+821012345678"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 내_SMS_발송_내역을_페이징_조회한다() {
        SmsNotificationLog log = SmsNotificationLog.builder()
                .recipientUserId(1L)
                .phoneNumber("+821012345678")
                .message("[게시판] 회원님의 글에 새 댓글이 달렸습니다.")
                .status(SmsStatus.SUCCESS)
                .build();
        ReflectionTestUtils.setField(log, "id", 1L);
        given(smsNotificationLogRepository.findByRecipientUserIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(log)));

        PageResponse<SmsNotificationLogResponse> response = userService.getMyNotificationLogs(1L, 0, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo(SmsStatus.SUCCESS);
    }
}
