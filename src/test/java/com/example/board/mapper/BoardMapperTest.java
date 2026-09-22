package com.example.board.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.entity.Board;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.BoardLike;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.repository.BoardLikeRepository;
import com.example.board.repository.BoardRepository;
import com.example.board.repository.UserRepository;

@SpringBootTest
@Transactional
class BoardMapperTest {

    @Autowired
    private BoardMapper boardMapper;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardLikeRepository boardLikeRepository;

    @Test
    void 키워드와_카테고리로_게시글_목록을_검색하고_페이징한다() {
        User user = userRepository.save(User.builder()
                .email("mapper-test@example.com")
                .password("password")
                .name("작성자")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());

        for (int i = 0; i < 3; i++) {
            boardRepository.save(Board.builder()
                    .title("자유게시판 제목 " + i)
                    .content("내용 " + i)
                    .category(BoardCategory.FREE)
                    .user(user)
                    .build());
        }
        boardRepository.save(Board.builder()
                .title("공지사항")
                .content("공지 내용")
                .category(BoardCategory.NOTICE)
                .user(user)
                .build());

        // 공유 로컬 DB에 다른 테스트/수동 확인용 데이터가 남아있을 수 있으므로, 이 테스트가 직접 만든
        // 데이터만 걸리도록 이 테스트 전용 키워드("자유게시판")로 좁혀서 검증한다.
        List<BoardListItemResponse> freeList = boardMapper.findList("자유게시판", BoardCategory.FREE, null, null, 0, 10);
        long freeCount = boardMapper.count("자유게시판", BoardCategory.FREE, null, null);

        assertThat(freeCount).isEqualTo(3);
        assertThat(freeList).hasSize(3);
        assertThat(freeList).allMatch(item -> item.category() == BoardCategory.FREE);
        assertThat(freeList).allMatch(item -> item.authorName().equals("작성자"));

        List<BoardListItemResponse> keywordResult = boardMapper.findList("공지사항", null, null, null, 0, 10);
        assertThat(keywordResult).hasSize(1);
        assertThat(keywordResult.get(0).title()).isEqualTo("공지사항");

        List<BoardListItemResponse> pagedResult = boardMapper.findList("자유게시판", BoardCategory.FREE, null, null, 2, 2);
        assertThat(pagedResult).hasSize(1);
    }

    @Test
    void 작성자_ID로_게시글_목록을_필터링한다() {
        User author = userRepository.save(User.builder()
                .email("author-filter@example.com")
                .password("password")
                .name("글쓴이")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());
        User other = userRepository.save(User.builder()
                .email("other-filter@example.com")
                .password("password")
                .name("다른사람")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());

        boardRepository.save(Board.builder()
                .title("작성자 글 1")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(author)
                .build());
        boardRepository.save(Board.builder()
                .title("작성자 글 2")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(author)
                .build());
        boardRepository.save(Board.builder()
                .title("다른 사람 글")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(other)
                .build());

        List<BoardListItemResponse> myList = boardMapper.findList(null, null, author.getId(), null, 0, 10);
        long myCount = boardMapper.count(null, null, author.getId(), null);

        assertThat(myCount).isEqualTo(2);
        assertThat(myList).hasSize(2);
        assertThat(myList).allMatch(item -> item.authorName().equals("글쓴이"));
    }

    @Test
    void 좋아요_수가_목록에_집계된다() {
        User user = userRepository.save(User.builder()
                .email("like-test@example.com")
                .password("password")
                .name("작성자")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());
        User liker = userRepository.save(User.builder()
                .email("liker@example.com")
                .password("password")
                .name("좋아요누른사람")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());

        Board liked = boardRepository.save(Board.builder()
                .title("좋아요 있는 글")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(user)
                .build());
        boardRepository.save(Board.builder()
                .title("좋아요 없는 글")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(user)
                .build());
        boardLikeRepository.save(BoardLike.builder().board(liked).user(liker).build());

        // authorId로 좁혀서 이 테스트가 만든 게시글만 조회한다(공유 로컬 DB의 다른 데이터에 영향받지 않도록).
        List<BoardListItemResponse> list = boardMapper.findList(null, BoardCategory.FREE, user.getId(), null, 0, 10);

        BoardListItemResponse likedItem = list.stream()
                .filter(item -> item.id().equals(liked.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(likedItem.likeCount()).isEqualTo(1L);
        assertThat(list.stream().filter(item -> !item.id().equals(liked.getId())))
                .allMatch(item -> item.likeCount() == 0L);
    }

    @Test
    void 좋아요_누른_사용자_ID로_게시글_목록을_필터링한다() {
        User author = userRepository.save(User.builder()
                .email("liked-list-author@example.com")
                .password("password")
                .name("작성자")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());
        User liker = userRepository.save(User.builder()
                .email("liked-list-liker@example.com")
                .password("password")
                .name("좋아요누른사람")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build());

        Board likedBoard = boardRepository.save(Board.builder()
                .title("좋아요 누른 글")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(author)
                .build());
        boardRepository.save(Board.builder()
                .title("좋아요 안 누른 글")
                .content("내용")
                .category(BoardCategory.FREE)
                .user(author)
                .build());
        boardLikeRepository.save(BoardLike.builder().board(likedBoard).user(liker).build());

        List<BoardListItemResponse> likedList = boardMapper.findList(null, null, null, liker.getId(), 0, 10);
        long likedCount = boardMapper.count(null, null, null, liker.getId());

        assertThat(likedCount).isEqualTo(1);
        assertThat(likedList).hasSize(1);
        assertThat(likedList.get(0).id()).isEqualTo(likedBoard.getId());
    }
}
