package com.example.board.security.oauth2;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.board.entity.Provider;
import com.example.board.entity.User;
import com.example.board.repository.UserRepository;
import com.example.board.security.AdminBootstrapPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final AdminBootstrapPolicy adminBootstrapPolicy;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo userInfo = resolveUserInfo(registrationId, oAuth2User.getAttributes());
        User user = findOrCreateUser(registrationId, userInfo);

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private User findOrCreateUser(String registrationId, OAuth2UserInfo userInfo) {
        return userRepository.findByEmail(userInfo.getEmail())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(userInfo.getEmail())
                                .name(userInfo.getName())
                                .provider(Provider.valueOf(registrationId.toUpperCase()))
                                .providerId(userInfo.getProviderId())
                                .role(adminBootstrapPolicy.resolveRole(userInfo.getEmail()))
                                .build()));
    }

    private OAuth2UserInfo resolveUserInfo(String registrationId, Map<String, Object> attributes) {
        if ("naver".equalsIgnoreCase(registrationId)) {
            Object response = attributes.get("response");
            if (!(response instanceof Map)) {
                throw new OAuth2AuthenticationException("네이버 사용자 정보 응답 형식이 올바르지 않습니다.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> naverAttributes = (Map<String, Object>) response;
            return new NaverUserInfo(naverAttributes);
        }
        if ("google".equalsIgnoreCase(registrationId)) {
            return new GoogleUserInfo(attributes);
        }
        throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + registrationId);
    }
}
