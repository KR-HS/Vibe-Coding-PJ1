package com.example.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.request.CommentCreateRequest;
import com.example.board.dto.request.CommentUpdateRequest;
import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.Comment;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.BoardNotFoundException;
import com.example.board.exception.CommentNotFoundException;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.repository.BoardRepository;
import com.example.board.repository.CommentRepository;
import com.example.board.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    private User author;
    private Board board;
    private Comment comment;

    @BeforeEach
    void setUp() {
        author = User.builder()
                .email("author@example.com")
                .password("encoded-password")
                .name("작성자")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(author, "id", 1L);

        board = Board.builder()
                .title("제목")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(author)
                .build();
        ReflectionTestUtils.setField(board, "id", 10L);

        comment = Comment.builder()
                .board(board)
                .user(author)
                .content("댓글 내용")
                .build();
        ReflectionTestUtils.setField(comment, "id", 100L);
    }

    @Test
    void 존재하지_않는_게시글의_댓글목록을_조회하면_예외가_발생한다() {
        given(boardRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> commentService.getList(999L))
                .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void 댓글을_작성하면_ID를_반환한다() {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(commentRepository.save(any(Comment.class))).willReturn(comment);

        Long commentId = commentService.create(1L, 10L, request);

        assertThat(commentId).isEqualTo(100L);
    }

    @Test
    void 존재하지_않는_게시글에_댓글을_작성하면_예외가_발생한다() {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");
        given(boardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(1L, 999L, request))
                .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void 작성자_본인이_댓글을_수정하면_성공한다() {
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        commentService.update(1L, 100L, request);

        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
    }

    @Test
    void 작성자가_아니면_댓글_수정시_예외가_발생한다() {
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        assertThatThrownBy(() -> commentService.update(2L, 100L, request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void 존재하지_않는_댓글을_수정하면_예외가_발생한다() {
        given(commentRepository.findById(999L)).willReturn(Optional.empty());
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        assertThatThrownBy(() -> commentService.update(1L, 999L, request))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void 작성자_본인은_댓글을_삭제할_수_있다() {
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.delete(1L, Role.USER, 100L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void ADMIN은_타인의_댓글도_삭제할_수_있다() {
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.delete(2L, Role.ADMIN, 100L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void 작성자도_ADMIN도_아니면_댓글_삭제시_예외가_발생한다() {
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(2L, Role.USER, 100L))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(commentRepository, never()).delete(any());
    }
}
