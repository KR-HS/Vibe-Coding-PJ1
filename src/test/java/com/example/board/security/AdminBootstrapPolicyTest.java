package com.example.board.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.board.entity.Role;

class AdminBootstrapPolicyTest {

    @Test
    void 부트스트랩_이메일과_일치하면_ADMIN을_반환한다() {
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("admin@example.com");

        assertThat(policy.resolveRole("admin@example.com")).isEqualTo(Role.ADMIN);
    }

    @Test
    void 대소문자가_달라도_부트스트랩_이메일과_일치하면_ADMIN을_반환한다() {
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("admin@example.com");

        assertThat(policy.resolveRole("ADMIN@EXAMPLE.COM")).isEqualTo(Role.ADMIN);
    }

    @Test
    void 부트스트랩_이메일과_다르면_USER를_반환한다() {
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("admin@example.com");

        assertThat(policy.resolveRole("other@example.com")).isEqualTo(Role.USER);
    }

    @Test
    void 부트스트랩_이메일이_비어있으면_항상_USER를_반환한다() {
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("");

        assertThat(policy.resolveRole("anyone@example.com")).isEqualTo(Role.USER);
    }
}
