package com.example.board.security.oauth2;

import java.util.Map;

/**
 * 네이버는 사용자 정보를 최상위가 아닌 "response" 객체로 감싸서 응답한다.
 * CustomOAuth2UserService에서 attributes.get("response")를 꺼내 이 클래스에 전달한다.
 */
public class NaverUserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public NaverUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("id");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }
}
