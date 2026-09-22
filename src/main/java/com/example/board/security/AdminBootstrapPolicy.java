package com.example.board.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.board.entity.Role;

@Component
public class AdminBootstrapPolicy {

    private final String adminBootstrapEmail;

    public AdminBootstrapPolicy(@Value("${admin.bootstrap-email:}") String adminBootstrapEmail) {
        this.adminBootstrapEmail = adminBootstrapEmail;
    }

    public Role resolveRole(String email) {
        boolean isBootstrapAdmin = !adminBootstrapEmail.isBlank() && adminBootstrapEmail.equalsIgnoreCase(email);
        return isBootstrapAdmin ? Role.ADMIN : Role.USER;
    }
}
