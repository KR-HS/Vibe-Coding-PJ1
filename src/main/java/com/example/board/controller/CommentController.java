package com.example.board.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.board.dto.request.CommentCreateRequest;
import com.example.board.dto.request.CommentUpdateRequest;
import com.example.board.dto.response.CommentResponse;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.CommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<List<CommentResponse>> getList(@PathVariable Long boardId) {
        return ResponseEntity.ok(commentService.getList(boardId));
    }

    @PostMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long boardId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long commentId = commentService.create(userDetails.getId(), boardId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/boards/" + boardId + "/comments/" + commentId))
                .build();
    }

    @PutMapping("/api/comments/{id}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request) {
        commentService.update(userDetails.getId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        commentService.delete(userDetails.getId(), userDetails.getUser().getRole(), id);
        return ResponseEntity.noContent().build();
    }
}
