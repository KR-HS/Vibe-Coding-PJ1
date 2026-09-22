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
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.dto.response.LikeResponse;
import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.BoardNotFoundException;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.mapper.BoardMapper;
import com.example.board.repository.AttachmentRepository;
import com.example.board.repository.BoardLikeRepository;
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
    private BoardLikeRepository boardLikeRepository;
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
        given(boardLikeRepository.countByBoardId(10L)).willReturn(0L);

        BoardDetailResponse response = boardService.getDetail(10L, null);

        assertThat(response.viewCount()).isEqualTo(1);
        assertThat(board.getViewCount()).isEqualTo(1);
        assertThat(response.liked()).isFalse();
    }

    @Test
    void 로그인한_사용자가_좋아요를_눌렀으면_상세조회에_반영된다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(attachmentRepository.findByBoardId(10L)).willReturn(List.of());
        given(boardLikeRepository.countByBoardId(10L)).willReturn(3L);
        given(boardLikeRepository.existsByBoardIdAndUserId(10L, 1L)).willReturn(true);

        BoardDetailResponse response = boardService.getDetail(10L, 1L);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.liked()).isTrue();
    }

    @Test
    void 존재하지_않는_게시글을_조회하면_예외가_발생한다() {
        given(boardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.getDetail(999L, null))
                .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void 좋아요를_누르면_좋아요가_추가되고_카운트를_반환한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(boardLikeRepository.existsByBoardIdAndUserId(10L, 2L)).willReturn(false);
        given(userRepository.getReferenceById(2L)).willReturn(author);
        given(boardLikeRepository.countByBoardId(10L)).willReturn(1L);

        LikeResponse response = boardService.like(2L, 10L);

        assertThat(response.likeCount()).isEqualTo(1L);
        assertThat(response.liked()).isTrue();
        verify(boardLikeRepository).save(any());
    }

    @Test
    void 이미_좋아요한_상태에서_다시_누르면_중복_저장하지_않는다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(boardLikeRepository.existsByBoardIdAndUserId(10L, 2L)).willReturn(true);
        given(boardLikeRepository.countByBoardId(10L)).willReturn(1L);

        LikeResponse response = boardService.like(2L, 10L);

        assertThat(response.likeCount()).isEqualTo(1L);
        assertThat(response.liked()).isTrue();
        verify(boardLikeRepository, never()).save(any());
    }

    @Test
    void 좋아요를_취소하면_좋아요가_삭제되고_카운트를_반환한다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(boardLikeRepository.countByBoardId(10L)).willReturn(0L);

        LikeResponse response = boardService.unlike(2L, 10L);

        assertThat(response.likeCount()).isEqualTo(0L);
        assertThat(response.liked()).isFalse();
        verify(boardLikeRepository).deleteByBoardIdAndUserId(10L, 2L);
    }

    @Test
    void 좋아요하지_않은_상태에서_취소해도_예외가_발생하지_않는다() {
        given(boardRepository.findById(10L)).willReturn(Optional.of(board));
        given(boardLikeRepository.countByBoardId(10L)).willReturn(0L);

        LikeResponse response = boardService.unlike(2L, 10L);

        assertThat(response.liked()).isFalse();
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
        verify(boardLikeRepository).deleteByBoardId(10L);
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

    @Test
    void 내가_쓴_게시글_목록을_조회한다() {
        BoardListItemResponse item = new BoardListItemResponse(
                10L, "제목", BoardCategory.FREE, "작성자", 0, 0L, 0L, null);
        given(boardMapper.findList(null, null, 1L, null, 0, 10)).willReturn(List.of(item));
        given(boardMapper.count(null, null, 1L, null)).willReturn(1L);

        PageResponse<BoardListItemResponse> response = boardService.getMyList(1L, 0, 10);

        assertThat(response.content()).containsExactly(item);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void 내가_좋아요_누른_게시글_목록을_조회한다() {
        BoardListItemResponse item = new BoardListItemResponse(
                10L, "제목", BoardCategory.FREE, "작성자", 0, 0L, 1L, null);
        given(boardMapper.findList(null, null, null, 1L, 0, 10)).willReturn(List.of(item));
        given(boardMapper.count(null, null, null, 1L)).willReturn(1L);

        PageResponse<BoardListItemResponse> response = boardService.getLikedList(1L, 0, 10);

        assertThat(response.content()).containsExactly(item);
        assertThat(response.totalElements()).isEqualTo(1L);
    }
}
