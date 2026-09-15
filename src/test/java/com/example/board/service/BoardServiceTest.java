package com.example.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.request.BoardCreateRequest;
import com.example.board.dto.request.BoardUpdateRequest;
import com.example.board.dto.response.BoardDetailResponse;
import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.BoardNotFoundException;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.mapper.BoardMapper;
import com.example.board.repository.AttachmentRepository;
import com.example.board.repository.BoardRepository;
import com.example.board.repository.CommentRepository;
import com.example.board.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private BoardMapper boardMapper;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private BoardService boardService;

    private User author;
    private Board board;

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
    }

    @Test
    void 게시글을_작성하면_ID를_반환한다() {
        BoardCreateRequest request = new BoardCreateRequest("제목", "내용", BoardCategory.FREE);
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(boardRepository.save(any(Board.class))).willAnswer(invocation -> {
            Board saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        Long boardId = boardService.create(1L, request, null);

        assertThat(boardId).isEqualTo(10L);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void 게시글_상세조회시_조회수가_증가한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(attachmentRepository.findByBoardId(10L)).willReturn(List.of());

        BoardDetailResponse response = boardService.getDetail(10L);

        assertThat(response.viewCount()).isEqualTo(1);
        assertThat(board.getViewCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_게시글을_조회하면_예외가_발생한다() {
        given(boardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.getDetail(999L))
                .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void 작성자_본인이_게시글을_수정하면_성공한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        BoardUpdateRequest request = new BoardUpdateRequest("수정 제목", "수정 내용", BoardCategory.QNA);

        boardService.update(1L, 10L, request);

        assertThat(board.getTitle()).isEqualTo("수정 제목");
        assertThat(board.getCategory()).isEqualTo(BoardCategory.QNA);
    }

    @Test
    void 작성자가_아니면_게시글_수정시_예외가_발생한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        BoardUpdateRequest request = new BoardUpdateRequest("수정 제목", "수정 내용", BoardCategory.QNA);

        assertThatThrownBy(() -> boardService.update(2L, 10L, request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void 작성자_본인은_게시글을_삭제할_수_있다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(attachmentRepository.findByBoardId(10L)).willReturn(List.of());

        boardService.delete(1L, Role.USER, 10L);

        verify(commentRepository).deleteByBoardId(10L);
        verify(boardRepository).delete(board);
    }

    @Test
    void ADMIN은_타인의_게시글도_삭제할_수_있다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(attachmentRepository.findByBoardId(10L)).willReturn(List.of());

        boardService.delete(2L, Role.ADMIN, 10L);

        verify(boardRepository).delete(board);
    }

    @Test
    void 작성자도_ADMIN도_아니면_게시글_삭제시_예외가_발생한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));

        assertThatThrownBy(() -> boardService.delete(2L, Role.USER, 10L))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(boardRepository, never()).delete(any());
    }
}
