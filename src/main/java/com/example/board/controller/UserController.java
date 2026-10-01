package com.example.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.board.dto.request.PhoneNumberUpdateRequest;
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.MyCommentResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.SmsNotificationLogResponse;
import com.example.board.dto.response.UserResponse;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;
import com.example.board.service.CommentService;
import com.example.board.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "회원(마이페이지)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final BoardService boardService;
    private final CommentService commentService;
    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(UserResponse.from(userDetails.getUser()));
    }

    @Operation(summary = "내가 쓴 게시글 목록 조회")
    @GetMapping("/me/boards")
    public ResponseEntity<PageResponse<BoardListItemResponse>> myBoards(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(boardService.getMyList(userDetails.getId(), page, size));
    }

    @Operation(summary = "내가 쓴 댓글 목록 조회")
    @GetMapping("/me/comments")
    public ResponseEntity<PageResponse<MyCommentResponse>> myComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(commentService.getMyList(userDetails.getId(), page, size));
    }

    @Operation(summary = "좋아요한 게시글 목록 조회")
    @GetMapping("/me/likes")
    public ResponseEntity<PageResponse<BoardListItemResponse>> myLikes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(boardService.getLikedList(userDetails.getId(), page, size));
    }

    @Operation(summary = "SMS 알림 수신용 전화번호 등록/수정")
    @PatchMapping("/me/phone")
    public ResponseEntity<Void> updatePhone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PhoneNumberUpdateRequest request) {
        userService.updatePhoneNumber(userDetails.getId(), request.phoneNumber());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 SMS 알림 발송 내역 조회")
    @GetMapping("/me/notifications")
    public ResponseEntity<PageResponse<SmsNotificationLogResponse>> myNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getMyNotificationLogs(userDetails.getId(), page, size));
    }
}
