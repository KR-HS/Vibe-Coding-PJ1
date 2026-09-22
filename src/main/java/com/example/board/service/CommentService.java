package com.example.board.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.board.dto.request.CommentCreateRequest;
import com.example.board.dto.request.CommentUpdateRequest;
import com.example.board.dto.response.CommentResponse;
import com.example.board.dto.response.MyCommentResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.Board;
import com.example.board.entity.Comment;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.BoardNotFoundException;
import com.example.board.exception.CommentNotFoundException;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.repository.BoardRepository;
import com.example.board.repository.CommentRepository;
import com.example.board.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    public List<CommentResponse> getList(Long boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new BoardNotFoundException(boardId);
        }
        return commentRepository.findByBoardIdOrderByCreatedAtAsc(boardId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    public PageResponse<MyCommentResponse> getMyList(Long userId, int page, int size) {
        Page<Comment> result = commentRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<MyCommentResponse> content = result.getContent().stream()
                .map(MyCommentResponse::from)
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional
    public Long create(Long userId, Long boardId, CommentCreateRequest request) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));
        User user = userRepository.getReferenceById(userId);

        Comment comment = Comment.builder()
                .board(board)
                .user(user)
                .content(request.content())
                .build();
        return commentRepository.save(comment).getId();
    }

    @Transactional
    public void update(Long userId, Long commentId, CommentUpdateRequest request) {
        Comment comment = getCommentOrThrow(commentId);
        if (!comment.isWrittenBy(userId)) {
            throw new ForbiddenOperationException("댓글 작성자만 수정할 수 있습니다.");
        }
        comment.update(request.content());
    }

    @Transactional
    public void delete(Long userId, Role role, Long commentId) {
        Comment comment = getCommentOrThrow(commentId);
        if (!comment.isWrittenBy(userId) && role != Role.ADMIN) {
            throw new ForbiddenOperationException("댓글 작성자 또는 관리자만 삭제할 수 있습니다.");
        }
        commentRepository.delete(comment);
    }

    private Comment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }
}
