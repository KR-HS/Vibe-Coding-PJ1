package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.example.board.dto.request.CommentUpdateRequest;
import com.example.board.dto.response.CommentResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.CommentService;

@WebMvcTest(
        controllers = CommentController.class,
        excludeAutoConfiguration = {OAuth2ClientAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class,
                MybatisAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @Test
    void 댓글_목록을_조회하면_200을_반환한다() throws Exception {
        CommentResponse response = new CommentResponse(1L, "댓글", 1L, "작성자", null, null);
        given(commentService.getList(1L)).willReturn(List.of(response));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/boards/1/comments"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].content").value("댓글"));
    }

    @Test
    void 댓글_수정에_성공하면_204를_반환하고_서비스를_호출한다() {
        CustomUserDetails userDetails = new CustomUserDetails(testUser());
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글");

        ResponseEntity<Void> response = new CommentController(commentService).update(userDetails, 100L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(commentService).update(1L, 100L, request);
    }

    @Test
    void 댓글_삭제에_성공하면_204를_반환하고_서비스를_호출한다() {
        CustomUserDetails userDetails = new CustomUserDetails(testUser());

        ResponseEntity<Void> response = new CommentController(commentService).delete(userDetails, 100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(commentService).delete(1L, Role.USER, 100L);
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
