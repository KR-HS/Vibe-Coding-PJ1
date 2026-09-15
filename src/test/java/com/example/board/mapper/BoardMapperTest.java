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
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
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

        List<BoardListItemResponse> freeList = boardMapper.findList(null, BoardCategory.FREE, 0, 10);
        long freeCount = boardMapper.count(null, BoardCategory.FREE);

        assertThat(freeCount).isEqualTo(3);
        assertThat(freeList).hasSize(3);
        assertThat(freeList).allMatch(item -> item.category() == BoardCategory.FREE);
        assertThat(freeList).allMatch(item -> item.authorName().equals("작성자"));

        List<BoardListItemResponse> keywordResult = boardMapper.findList("공지", null, 0, 10);
        assertThat(keywordResult).hasSize(1);
        assertThat(keywordResult.get(0).title()).isEqualTo("공지사항");

        List<BoardListItemResponse> pagedResult = boardMapper.findList(null, BoardCategory.FREE, 2, 2);
        assertThat(pagedResult).hasSize(1);
    }
}
