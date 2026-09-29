package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.request.PhoneNumberUpdateRequest;
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.MyCommentResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.SmsNotificationLogResponse;
import com.example.board.dto.response.UserResponse;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.SmsStatus;
import com.example.board.entity.User;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;
import com.example.board.service.CommentService;
import com.example.board.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private BoardService boardService;
    @Mock
    private CommentService commentService;
    @Mock
    private UserService userService;

    private UserController userController;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userController = new UserController(boardService, commentService, userService);

        User user = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void 인증된_사용자는_내_정보를_조회할_수_있다() {
        ResponseEntity<UserResponse> response = userController.me(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(1L);
        assertThat(response.getBody().email()).isEqualTo("user@example.com");
        assertThat(response.getBody().role()).isEqualTo(Role.USER);
    }

    @Test
    void 내가_쓴_게시글_목록을_조회할_수_있다() {
        BoardListItemResponse item = new BoardListItemResponse(
                10L, "제목", BoardCategory.FREE, "홍길동", 0, 0L, 0L, null);
        given(boardService.getMyList(1L, 0, 10)).willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        ResponseEntity<PageResponse<BoardListItemResponse>> response = userController.myBoards(userDetails, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).containsExactly(item);
    }

    @Test
    void 내가_쓴_댓글_목록을_조회할_수_있다() {
        MyCommentResponse item = new MyCommentResponse(100L, "댓글 내용", 10L, "제목", null, null);
        given(commentService.getMyList(1L, 0, 10)).willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        ResponseEntity<PageResponse<MyCommentResponse>> response = userController.myComments(userDetails, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).containsExactly(item);
    }

    @Test
    void 내가_좋아요_누른_게시글_목록을_조회할_수_있다() {
        BoardListItemResponse item = new BoardListItemResponse(
                10L, "제목", BoardCategory.FREE, "다른작성자", 0, 0L, 1L, null);
        given(boardService.getLikedList(1L, 0, 10)).willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        ResponseEntity<PageResponse<BoardListItemResponse>> response = userController.myLikes(userDetails, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).containsExactly(item);
    }

    @Test
    void 내_전화번호를_등록할_수_있다() {
        PhoneNumberUpdateRequest request = new PhoneNumberUpdateRequest("+821012345678");

        ResponseEntity<Void> response = userController.updatePhone(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).updatePhoneNumber(eq(1L), eq("+821012345678"));
    }

    @Test
    void 내_SMS_발송_내역을_조회할_수_있다() {
        SmsNotificationLogResponse item = new SmsNotificationLogResponse(1L, "[게시판] 회원님의 글에 새 댓글이 달렸습니다.",
                SmsStatus.SUCCESS, null, null);
        given(userService.getMyNotificationLogs(1L, 0, 10)).willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        ResponseEntity<PageResponse<SmsNotificationLogResponse>> response = userController.myNotifications(userDetails, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).containsExactly(item);
    }
}
