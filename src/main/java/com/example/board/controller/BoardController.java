package com.example.board.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.board.dto.request.BoardCreateRequest;
import com.example.board.dto.request.BoardUpdateRequest;
import com.example.board.dto.response.BoardDetailResponse;
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.LikeResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.BoardCategory;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "게시글")
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "게시글 목록 조회 (검색/카테고리 필터/페이징)")
    @GetMapping
    public ResponseEntity<PageResponse<BoardListItemResponse>> getList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoardCategory category) {
        return ResponseEntity.ok(boardService.getList(keyword, category, page, size));
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<BoardDetailResponse> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(boardService.getDetail(id, userId));
    }

    @Operation(summary = "게시글 좋아요")
    @PostMapping("/{id}/likes")
    public ResponseEntity<LikeResponse> like(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(boardService.like(userDetails.getId(), id));
    }

    @Operation(summary = "게시글 좋아요 취소")
    @DeleteMapping("/{id}/likes")
    public ResponseEntity<LikeResponse> unlike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(boardService.unlike(userDetails.getId(), id));
    }

    @Operation(summary = "게시글 작성 (첨부파일 포함 가능)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("request") BoardCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long boardId = boardService.create(userDetails.getId(), request, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/boards/" + boardId))
                .build();
    }

    @Operation(summary = "게시글 수정")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BoardUpdateRequest request) {
        boardService.update(userDetails.getId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        boardService.delete(userDetails.getId(), userDetails.getUser().getRole(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "첨부파일 다운로드")
    @GetMapping("/{boardId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long boardId,
            @PathVariable Long attachmentId) {
        BoardService.AttachmentDownload download = boardService.getAttachmentDownload(boardId, attachmentId);
        String contentType = download.contentType() != null
                ? download.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String encodedFilename = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(download.resource());
    }

    @Operation(summary = "첨부파일 삭제")
    @DeleteMapping("/{boardId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long boardId,
            @PathVariable Long attachmentId) {
        boardService.deleteAttachment(userDetails.getId(), userDetails.getUser().getRole(), boardId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
