package com.example.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.MyCommentResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.UserResponse;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;
import com.example.board.service.CommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final BoardService boardService;
    private final CommentService commentService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(UserResponse.from(userDetails.getUser()));
    }

    @GetMapping("/me/boards")
    public ResponseEntity<PageResponse<BoardListItemResponse>> myBoards(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(boardService.getMyList(userDetails.getId(), page, size));
    }

    @GetMapping("/me/comments")
    public ResponseEntity<PageResponse<MyCommentResponse>> myComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(commentService.getMyList(userDetails.getId(), page, size));
    }

    @GetMapping("/me/likes")
    public ResponseEntity<PageResponse<BoardListItemResponse>> myLikes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(boardService.getLikedList(userDetails.getId(), page, size));
    }
}
