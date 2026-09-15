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
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.BoardCategory;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<PageResponse<BoardListItemResponse>> getList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BoardCategory category) {
        return ResponseEntity.ok(boardService.getList(keyword, category, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.getDetail(id));
    }

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

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody BoardUpdateRequest request) {
        boardService.update(userDetails.getId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        boardService.delete(userDetails.getId(), userDetails.getUser().getRole(), id);
        return ResponseEntity.noContent().build();
    }

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

    @DeleteMapping("/{boardId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long boardId,
            @PathVariable Long attachmentId) {
        boardService.deleteAttachment(userDetails.getId(), userDetails.getUser().getRole(), boardId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
