package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.board.dto.request.LoginRequest;
import com.example.board.dto.request.ReissueRequest;
import com.example.board.dto.request.SignupRequest;
import com.example.board.dto.response.TokenResponse;
import com.example.board.dto.response.UserResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.DuplicateEmailException;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.AuthService;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.ObjectMapper;

import org.springframework.test.util.ReflectionTestUtils;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {OAuth2ClientAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입_요청이_성공하면_201을_반환한다() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "password123", "홍길동");
        given(authService.signup(any())).willReturn(new UserResponse(1L, "user@example.com", "홍길동", Role.USER));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void 이메일이_중복되면_회원가입은_409를_반환한다() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "password123", "홍길동");
        given(authService.signup(any())).willThrow(new DuplicateEmailException(request.email()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void 요청_형식이_올바르지_않으면_회원가입은_400을_반환한다() throws Exception {
        SignupRequest invalidRequest = new SignupRequest("not-an-email", "short", "");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void 로그인에_성공하면_토큰을_반환한다() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        given(authService.login(any())).willReturn(new TokenResponse("access-token", "refresh-token"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void 재발급에_성공하면_새_토큰을_반환한다() throws Exception {
        ReissueRequest request = new ReissueRequest("refresh-token");
        given(authService.reissue(any())).willReturn(new TokenResponse("new-access-token", "new-refresh-token"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void 로그아웃하면_204를_반환하고_인증된_사용자의_토큰을_무효화한다() {
        User user = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        ResponseEntity<Void> response = new AuthController(authService).logout(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout(1L);
    }
}
