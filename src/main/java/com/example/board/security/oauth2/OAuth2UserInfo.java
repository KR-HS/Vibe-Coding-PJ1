package com.example.board.security.oauth2;

public interface OAuth2UserInfo {

    String getProviderId();

    String getEmail();

    String getName();
}
