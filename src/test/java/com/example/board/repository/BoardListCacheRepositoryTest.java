package com.example.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.BoardCategory;

@SpringBootTest
class BoardListCacheRepositoryTest {

    @Autowired
    private BoardListCacheRepository boardListCacheRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(redisTemplate.keys("board-list:*"));
    }

    @Test
    void 저장한_목록을_같은_조건으로_조회하면_그대로_돌아온다() {
        BoardListItemResponse item = new BoardListItemResponse(
                1L, "제목", BoardCategory.FREE, "작성자", 0, 0L, 0L, null);
        PageResponse<BoardListItemResponse> response = PageResponse.of(List.of(item), 0, 10, 1L);

        boardListCacheRepository.save("키워드", BoardCategory.FREE, 0, 10, response);
        Optional<PageResponse<BoardListItemResponse>> found = boardListCacheRepository.find("키워드", BoardCategory.FREE, 0, 10);

        assertThat(found).isPresent();
        assertThat(found.get().content()).hasSize(1);
        assertThat(found.get().content().get(0).title()).isEqualTo("제목");
    }

    @Test
    void 저장하지_않은_조건으로_조회하면_비어있다() {
        Optional<PageResponse<BoardListItemResponse>> found = boardListCacheRepository.find("없는키워드", null, 0, 10);

        assertThat(found).isEmpty();
    }

    @Test
    void 무효화하면_이전에_저장한_키로는_더이상_조회되지_않는다() {
        BoardListItemResponse item = new BoardListItemResponse(
                1L, "제목", BoardCategory.FREE, "작성자", 0, 0L, 0L, null);
        PageResponse<BoardListItemResponse> response = PageResponse.of(List.of(item), 0, 10, 1L);
        boardListCacheRepository.save(null, null, 0, 10, response);
        assertThat(boardListCacheRepository.find(null, null, 0, 10)).isPresent();

        boardListCacheRepository.invalidate();

        assertThat(boardListCacheRepository.find(null, null, 0, 10)).isEmpty();
    }
}
