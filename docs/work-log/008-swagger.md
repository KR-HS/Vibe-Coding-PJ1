# 008. Swagger(springdoc-openapi) API 문서화 - Work Log

## 2026-10-01

### 작업 내용
`docs/plans/008-swagger.md`에 따라 springdoc-openapi로 REST API 25개(5개 컨트롤러)를 Swagger UI로 문서화했다.

### 변경된 파일

**신규**
```
docs/plans/008-swagger.md
src/main/java/com/example/board/config/OpenApiConfig.java
```

**수정**
```
pom.xml                                                          # springdoc-openapi-starter-webmvc-ui 추가
src/main/resources/application.properties                        # springdoc.paths-to-match=/api/**
src/main/java/com/example/board/security/SecurityConfig.java     # swagger-ui/v3 api-docs permitAll 추가
src/main/java/com/example/board/controller/AuthController.java   # @Tag, @Operation 추가
src/main/java/com/example/board/controller/BoardController.java  # @Tag, @Operation 추가
src/main/java/com/example/board/controller/CommentController.java # @Tag, @Operation 추가
src/main/java/com/example/board/controller/AdminController.java  # @Tag, @Operation 추가
src/main/java/com/example/board/controller/UserController.java   # @Tag, @Operation 추가
```

### 주요 변경사항
- `OpenApiConfig`에서 전역 `bearerAuth` HTTP Bearer 보안 스킴을 등록해, Swagger UI의 Authorize 버튼에 JWT 액세스 토큰을 넣으면 인증이 필요한 API도 바로 "Try it out"으로 호출 가능하다.
- `springdoc.paths-to-match=/api/**`로 설정해서 정적 페이지 라우팅용 `MainController`(`/login`)는 문서화 대상에서 제외했다.
- 5개 REST 컨트롤러(인증/게시글/댓글/회원(마이페이지)/관리자)에 `@Tag`, 25개 엔드포인트 전부에 `@Operation(summary=...)` 한글 설명을 달았다.

### 테스트 결과
- `./mvnw test` 전체 100건 통과(BUILD SUCCESS) — springdoc 추가로 인한 부작용 없음.
- `/v3/api-docs` JSON을 직접 확인해서 18개 경로(25개 오퍼레이션)가 정확히 노출되고, `MainController`의 `/login`은 빠져있는 것을 확인.
- Playwright로 `/swagger-ui.html` 접속, 태그 5개(댓글/게시글/관리자/인증/회원(마이페이지))가 전부 정상 렌더링되는 것을 스크린샷으로 확인.
- 실제 회원가입 → 로그인으로 받은 JWT를 Swagger UI Authorize 버튼에 넣고, `GET /api/users/me`를 Try it out으로 직접 호출해서 실제 내 정보(id/email/name/role)가 담긴 200 응답을 받는 것까지 종단간으로 확인.
- 검증에 사용한 테스트 계정은 확인 후 삭제했다.

### 발생한 문제 및 해결 방법
- 별다른 문제 없이 Plan대로 한 번에 동작했다. 006(JMeter)/007(SMS)에서 반복됐던 "세분화된 스타터 때문에 자동 설정 빈이 빠져있는" 문제가 이번엔 발생하지 않았다.
- Playwright 검증 스크립트 작성 중, Swagger UI가 기본적으로 모든 태그/오퍼레이션을 펼친 상태로 렌더링한다는 걸 모르고 태그 헤더를 한 번 더 클릭했다가 오히려 접어버린 해프닝이 있었다 — 앱 코드와는 무관한 테스트 스크립트 실수였다.

### Plan과 실제 구현의 차이점
`docs/plans/008-swagger.md`의 "구현 결과와의 차이점" 절 참고. 유일한 차이는 예상했던 추가 의존성 문제가 이번엔 없었다는 점이고, 나머지는 계획대로 구현됐다.
