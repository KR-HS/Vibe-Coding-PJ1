package com.example.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.example.board.dto.request.RoleUpdateRequest;
import com.example.board.dto.response.AdminUserResponse;
import com.example.board.dto.response.PageResponse;
import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.User;
import com.example.board.security.CustomUserDetails;
import com.example.board.service.AdminService;

@WebMvcTest(
        controllers = AdminController.class,
        excludeAutoConfiguration = {OAuth2ClientAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class,
                MybatisAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @Test
    void 회원_목록을_조회하면_200을_반환한다() throws Exception {
        AdminUserResponse item = new AdminUserResponse(1L, "user@example.com", "홍길동", Role.USER, Provider.LOCAL, null);
        given(adminService.getUsers(isNull(), eq(0), eq(10)))
                .willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].email").value("user@example.com"));
    }

    @Test
    void 키워드로_회원을_검색하면_200을_반환한다() throws Exception {
        AdminUserResponse item = new AdminUserResponse(1L, "user@example.com", "홍길동", Role.USER, Provider.LOCAL, null);
        given(adminService.getUsers(eq("홍길동"), eq(0), eq(10)))
                .willReturn(PageResponse.of(List.of(item), 0, 10, 1L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").param("keyword", "홍길동"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void 권한_변경에_성공하면_204를_반환하고_서비스를_호출한다() {
        User admin = User.builder()
                .email("admin@example.com")
                .name("관리자")
                .provider(Provider.LOCAL)
                .role(Role.ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        CustomUserDetails userDetails = new CustomUserDetails(admin);
        RoleUpdateRequest request = new RoleUpdateRequest(Role.ADMIN);

        ResponseEntity<Void> response = new AdminController(adminService).changeRole(userDetails, 2L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(adminService).changeRole(1L, 2L, Role.ADMIN);
    }
}
