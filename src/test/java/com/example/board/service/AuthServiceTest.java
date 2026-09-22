package com.example.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.dto.request.LoginRequest;
import com.example.board.dto.request.ReissueRequest;
import com.example.board.dto.request.SignupRequest;
import com.example.board.dto.response.TokenResponse;
import com.example.board.dto.response.UserResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.exception.DuplicateEmailException;
import com.example.board.exception.InvalidTokenException;
import com.example.board.repository.RefreshTokenRepository;
import com.example.board.repository.UserRepository;
import com.example.board.security.AdminBootstrapPolicy;
import com.example.board.security.CustomUserDetails;
import com.example.board.security.jwt.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AdminBootstrapPolicy adminBootstrapPolicy;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("user@example.com")
                .password("encoded-password")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void 회원가입에_성공하면_저장된_사용자_정보를_반환한다() {
        SignupRequest request = new SignupRequest("user@example.com", "password123", "홍길동");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(adminBootstrapPolicy.resolveRole(request.email())).willReturn(Role.USER);
        given(userRepository.save(any(User.class))).willReturn(user);

        UserResponse response = authService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void 부트스트랩_관리자_이메일로_가입하면_ADMIN으로_생성된다() {
        SignupRequest request = new SignupRequest("admin@example.com", "password123", "관리자");
        User adminUser = User.builder()
                .email("admin@example.com")
                .password("encoded-password")
                .name("관리자")
                .provider(Provider.LOCAL)
                .role(Role.ADMIN)
                .build();
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(adminBootstrapPolicy.resolveRole(request.email())).willReturn(Role.ADMIN);
        given(userRepository.save(any(User.class))).willReturn(adminUser);

        UserResponse response = authService.signup(request);

        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void 이미_가입된_이메일로_회원가입하면_예외가_발생한다() {
        SignupRequest request = new SignupRequest("user@example.com", "password123", "홍길동");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 로그인에_성공하면_토큰을_발급하고_Refresh_Token을_저장한다() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, new CustomUserDetails(user).getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(jwtTokenProvider.generateAccessToken(1L, Role.USER)).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(1L)).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenValiditySeconds()).willReturn(1_209_600L);

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(1L, "refresh-token", 1_209_600L);
    }

    @Test
    void 유효하지_않은_Refresh_Token으로_재발급하면_예외가_발생한다() {
        ReissueRequest request = new ReissueRequest("invalid-token");
        given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void Redis에_저장된_Refresh_Token이_없으면_재발급에_실패한다() {
        ReissueRequest request = new ReissueRequest("refresh-token");
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void 저장된_Refresh_Token과_일치하지_않으면_재발급에_실패한다() {
        ReissueRequest request = new ReissueRequest("refresh-token");
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.of("other-token"));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void 정상적인_Refresh_Token이면_토큰을_재발급한다() {
        ReissueRequest request = new ReissueRequest("refresh-token");
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(1L);
        given(refreshTokenRepository.find(1L)).willReturn(Optional.of("refresh-token"));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(1L, Role.USER)).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(1L)).willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenValiditySeconds()).willReturn(1_209_600L);

        TokenResponse response = authService.reissue(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void 로그아웃하면_저장된_Refresh_Token을_삭제한다() {
        authService.logout(1L);

        verify(refreshTokenRepository, times(1)).delete(1L);
    }
}
