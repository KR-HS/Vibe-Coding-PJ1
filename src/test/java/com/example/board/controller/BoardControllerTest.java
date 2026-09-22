package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.example.board.dto.request.BoardUpdateRequest;
import com.example.board.dto.response.BoardDetailResponse;
import com.example.board.dto.response.BoardListItemResponse;
import com.example.board.dto.response.LikeResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.BoardCategory;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.ForbiddenOperationException;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.BoardService;

@WebMvcTest(
        controllers = BoardController.class,
        excludeAutoConfiguration = {OAuth2ClientAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class,
                MybatisAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @Test
    void 게시글_목록을_조회하면_200을_반환한다() throws Exception {
        BoardListItemResponse item = new BoardListItemResponse(
                1L, "제목", BoardCategory.FREE, "작성자", 0, 0, 0, null);
        given(boardService.getList(any(), any(), eq(0), eq(10)))
                .willReturn(PageResponse.of(List.of(item), 0, 10, 1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/boards"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].title").value("제목"));
    }

    @Test
    void 비로그인_사용자가_게시글_상세를_조회하면_liked가_false로_내려온다() {
        BoardDetailResponse response = new BoardDetailResponse(
                1L, "제목", "내용", BoardCategory.FREE, 1L, "작성자", 1, 0, false, List.of(), null, null);
        given(boardService.getDetail(1L, null)).willReturn(response);

        ResponseEntity<BoardDetailResponse> result = new BoardController(boardService).getDetail(null, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().liked()).isFalse();
    }

    @Test
    void 로그인_사용자가_게시글_상세를_조회하면_본인_ID로_liked를_계산한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        BoardDetailResponse response = new BoardDetailResponse(
                1L, "제목", "내용", BoardCategory.FREE, 2L, "작성자", 1, 1, true, List.of(), null, null);
        given(boardService.getDetail(1L, 1L)).willReturn(response);

        ResponseEntity<BoardDetailResponse> result = new BoardController(boardService).getDetail(userDetails, 1L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().liked()).isTrue();
    }

    @Test
    void 좋아요를_누르면_200과_좋아요_수를_반환한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        given(boardService.like(1L, 1L)).willReturn(new LikeResponse(1L, true));

        ResponseEntity<LikeResponse> response = new BoardController(boardService).like(userDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().likeCount()).isEqualTo(1L);
        assertThat(response.getBody().liked()).isTrue();
    }

    @Test
    void 좋아요를_취소하면_200과_좋아요_수를_반환한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        given(boardService.unlike(1L, 1L)).willReturn(new LikeResponse(0L, false));

        ResponseEntity<LikeResponse> response = new BoardController(boardService).unlike(userDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().likeCount()).isEqualTo(0L);
        assertThat(response.getBody().liked()).isFalse();
    }

    @Test
    void 게시글_수정에_성공하면_204를_반환하고_서비스를_호출한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        BoardUpdateRequest request = new BoardUpdateRequest("수정 제목", "수정 내용", BoardCategory.QNA);

        ResponseEntity<Void> response = new BoardController(boardService).update(userDetails, 1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).update(1L, 1L, request);
    }

    @Test
    void 작성자가_아니면_게시글_수정시_예외가_전파된다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        BoardUpdateRequest request = new BoardUpdateRequest("수정 제목", "수정 내용", BoardCategory.QNA);
        doThrow(new ForbiddenOperationException("게시글 작성자만 수정할 수 있습니다."))
                .when(boardService).update(1L, 1L, request);

        assertThatThrownBy(() -> new BoardController(boardService).update(userDetails, 1L, request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void 게시글_삭제에_성공하면_204를_반환하고_서비스를_호출한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        ResponseEntity<Void> response = new BoardController(boardService).delete(userDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).delete(1L, Role.USER, 1L);
    }

    @Test
    void 첨부파일을_다운로드하면_200을_반환한다() throws Exception {
        BoardService.AttachmentDownload download = new BoardService.AttachmentDownload(
                new ByteArrayResource("data".getBytes()), "file.txt", "text/plain");
        given(boardService.getAttachmentDownload(1L, 2L)).willReturn(download);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/boards/1/attachments/2"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void 첨부파일_삭제에_성공하면_204를_반환하고_서비스를_호출한다() {
        User user = testUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        ResponseEntity<Void> response = new BoardController(boardService).deleteAttachment(userDetails, 1L, 2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).deleteAttachment(1L, Role.USER, 1L, 2L);
    }

    private User testUser() {
        User user = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
