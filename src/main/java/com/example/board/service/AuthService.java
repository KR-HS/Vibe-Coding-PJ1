package com.example.board.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminBootstrapPolicy adminBootstrapPolicy;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .provider(Provider.LOCAL)
                .role(adminBootstrapPolicy.resolveRole(request.email()))
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return issueTokens(userDetails.getId(), userDetails.getUser().getRole());
    }

    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 Refresh Token입니다.");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String savedToken = refreshTokenRepository.find(userId)
                .orElseThrow(() -> new InvalidTokenException("로그인이 만료되었습니다. 다시 로그인해 주세요."));
        if (!savedToken.equals(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 Refresh Token입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("존재하지 않는 사용자입니다."));
        return issueTokens(user.getId(), user.getRole());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.delete(userId);
    }

    private TokenResponse issueTokens(Long userId, Role role) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        refreshTokenRepository.save(userId, refreshToken, jwtTokenProvider.getRefreshTokenValiditySeconds());
        return new TokenResponse(accessToken, refreshToken);
    }
}
