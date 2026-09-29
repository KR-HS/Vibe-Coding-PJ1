package com.example.board.service;

import java.util.List;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.board.dto.request.BoardCreateRequest;
import com.example.board.dto.request.BoardUpdateRequest;
import com.example.board.dto.response.AttachmentResponse;
import com.example.board.dto.response.BoardDetailResponse;
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.LikeResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.Attachment;
import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.BoardLike;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.AttachmentNotFoundException;
import com.example.board.exception.BoardNotFoundException;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.mapper.BoardMapper;
import com.example.board.repository.AttachmentRepository;
import com.example.board.repository.BoardLikeRepository;
import com.example.board.repository.BoardListCacheRepository;
import com.example.board.repository.BoardRepository;
import com.example.board.repository.CommentRepository;
import com.example.board.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final BoardLikeRepository boardLikeRepository;
    private final BoardListCacheRepository boardListCacheRepository;
    private final BoardMapper boardMapper;
    private final FileStorageService fileStorageService;

    public PageResponse<BoardListItemResponse> getList(String keyword, BoardCategory category, int page, int size) {
        Optional<PageResponse<BoardListItemResponse>> cached = boardListCacheRepository.find(keyword, category, page, size);
        if (cached.isPresent()) {
            return cached.get();
        }

        int offset = page * size;
        List<BoardListItemResponse> content = boardMapper.findList(keyword, category, null, null, offset, size);
        long totalElements = boardMapper.count(keyword, category, null, null);
        PageResponse<BoardListItemResponse> response = PageResponse.of(content, page, size, totalElements);

        boardListCacheRepository.save(keyword, category, page, size, response);
        return response;
    }

    public PageResponse<BoardListItemResponse> getMyList(Long userId, int page, int size) {
        int offset = page * size;
        List<BoardListItemResponse> content = boardMapper.findList(null, null, userId, null, offset, size);
        long totalElements = boardMapper.count(null, null, userId, null);
        return PageResponse.of(content, page, size, totalElements);
    }

    public PageResponse<BoardListItemResponse> getLikedList(Long userId, int page, int size) {
        int offset = page * size;
        List<BoardListItemResponse> content = boardMapper.findList(null, null, null, userId, offset, size);
        long totalElements = boardMapper.count(null, null, null, userId);
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    public BoardDetailResponse getDetail(Long boardId, Long userId) {
        Board board = getBoardOrThrow(boardId);
        board.increaseViewCount();

        List<AttachmentResponse> attachments = attachmentRepository.findByBoardId(boardId).stream()
                .map(AttachmentResponse::from)
                .toList();
        long likeCount = boardLikeRepository.countByBoardId(boardId);
        boolean liked = userId != null && boardLikeRepository.existsByBoardIdAndUserId(boardId, userId);
        return BoardDetailResponse.of(board, likeCount, liked, attachments);
    }

    @Transactional
    public LikeResponse like(Long userId, Long boardId) {
        Board board = getBoardOrThrow(boardId);
        if (!boardLikeRepository.existsByBoardIdAndUserId(boardId, userId)) {
            boardLikeRepository.save(BoardLike.builder()
                    .board(board)
                    .user(userRepository.getReferenceById(userId))
                    .build());
        }
        return new LikeResponse(boardLikeRepository.countByBoardId(boardId), true);
    }

    @Transactional
    public LikeResponse unlike(Long userId, Long boardId) {
        getBoardOrThrow(boardId);
        boardLikeRepository.deleteByBoardIdAndUserId(boardId, userId);
        return new LikeResponse(boardLikeRepository.countByBoardId(boardId), false);
    }

    @Transactional
    public Long create(Long userId, BoardCreateRequest request, List<MultipartFile> files) {
        User user = userRepository.getReferenceById(userId);
        Board board = Board.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .user(user)
                .build();
        boardRepository.save(board);

        if (files != null) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                FileStorageService.StoredFile stored = fileStorageService.store(board.getId(), file);
                attachmentRepository.save(Attachment.builder()
                        .board(board)
                        .originalFilename(stored.originalFilename())
                        .storedFilename(stored.storedFilename())
                        .filePath(stored.filePath())
                        .fileSize(stored.fileSize())
                        .contentType(stored.contentType())
                        .build());
            }
        }

        boardListCacheRepository.invalidate();
        return board.getId();
    }

    @Transactional
    public void update(Long userId, Long boardId, BoardUpdateRequest request) {
        Board board = getBoardOrThrow(boardId);
        if (!board.isWrittenBy(userId)) {
            throw new ForbiddenOperationException("게시글 작성자만 수정할 수 있습니다.");
        }
        board.update(request.title(), request.content(), request.category());
        boardListCacheRepository.invalidate();
    }

    @Transactional
    public void delete(Long userId, Role role, Long boardId) {
        Board board = getBoardOrThrow(boardId);
        if (!board.isWrittenBy(userId) && role != Role.ADMIN) {
            throw new ForbiddenOperationException("게시글 작성자 또는 관리자만 삭제할 수 있습니다.");
        }

        List<Attachment> attachments = attachmentRepository.findByBoardId(boardId);
        attachments.forEach(attachment -> fileStorageService.delete(attachment.getFilePath()));
        attachmentRepository.deleteAll(attachments);

        commentRepository.deleteByBoardId(boardId);
        boardLikeRepository.deleteByBoardId(boardId);
        boardRepository.delete(board);
        boardListCacheRepository.invalidate();
    }

    public AttachmentDownload getAttachmentDownload(Long boardId, Long attachmentId) {
        Attachment attachment = getAttachmentOrThrow(boardId, attachmentId);
        Resource resource = new FileSystemResource(fileStorageService.resolve(attachment.getFilePath()));
        return new AttachmentDownload(resource, attachment.getOriginalFilename(), attachment.getContentType());
    }

    @Transactional
    public void deleteAttachment(Long userId, Role role, Long boardId, Long attachmentId) {
        Board board = getBoardOrThrow(boardId);
        if (!board.isWrittenBy(userId) && role != Role.ADMIN) {
            throw new ForbiddenOperationException("게시글 작성자 또는 관리자만 첨부파일을 삭제할 수 있습니다.");
        }

        Attachment attachment = getAttachmentOrThrow(boardId, attachmentId);
        fileStorageService.delete(attachment.getFilePath());
        attachmentRepository.delete(attachment);
    }

    private Board getBoardOrThrow(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));
    }

    private Attachment getAttachmentOrThrow(Long boardId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        if (!attachment.getBoard().getId().equals(boardId)) {
            throw new AttachmentNotFoundException(attachmentId);
        }
        return attachment;
    }

    public record AttachmentDownload(Resource resource, String filename, String contentType) {
    }
}
