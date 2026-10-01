# 008. Swagger(springdoc-openapi) API 문서화

## 작업 목적
REST API 26개(5개 컨트롤러)를 Swagger UI로 자동 문서화해서, 컨트롤러 코드를 직접 뒤지지 않아도 전체 API 목록·요청/응답 형식을 한눈에 볼 수 있게 하고, JWT 인증이 필요한 API도 Swagger UI에서 토큰만 넣으면 바로 테스트해볼 수 있게 한다.

## 요구사항
- [ ] `/swagger-ui.html` 접속 시 전체 API 문서가 보인다
- [ ] `/api/**` 경로만 문서화 대상에 포함한다 (정적 페이지 라우팅용 `MainController`는 제외)
- [ ] 컨트롤러별로 태그(인증/게시글/댓글/회원/관리자)가 구분되어 보인다
- [ ] 각 엔드포인트에 한글 요약 설명이 달려있다
- [ ] Swagger UI의 Authorize 버튼에 JWT 액세스 토큰을 넣으면, 인증이 필요한 API도 "Try it out"으로 바로 호출해볼 수 있다
- [ ] Swagger 관련 경로는 인증 없이 접근 가능하다

### 확인된 사용자 결정 사항
- 별도 요구사항 없이 "진행해줘"로 승인된 작업이라, 아래는 기본값으로 판단해서 설계했다. 이견 있으면 구현 전에 알려달라고 안내한다.
- 이 프로젝트는 개인 학습용이라 운영/개발 profile을 나눠 Swagger를 끄고 켜는 설정은 하지 않는다 (항상 켜둠).
- springdoc-openapi **3.1.1** 사용 — Spring Boot 4 / Spring Framework 7을 공식 지원하는 버전이고, Java 17 최소 요구사항도 이 프로젝트와 맞는다.
- 에러 응답(`ErrorResponse`)은 springdoc이 기본으로 생성하는 스키마만 쓰고, 에러 코드별 `@ApiResponse` 애노테이션까지는 이번 범위에서 달지 않는다 (26개 엔드포인트 전부에 달면 장황해지고, CLAUDE.md의 "요청 범위를 벗어난 작업 금지"에도 맞지 않음).

## 현재 구조 및 관련 코드
- 컨트롤러는 `AdminController`, `AuthController`, `BoardController`, `CommentController`, `UserController`(REST, `/api/**`) + `MainController`(`/login` → 정적 페이지 리다이렉트, REST 아님) 총 6개, API 엔드포인트 25개.
- `SecurityConfig.PERMIT_ALL_PATHS`에 Swagger 관련 경로를 추가해야 한다 (현재는 `/api/auth/**` 등만 permitAll, 나머지 `/api/**`는 인증 필요).
- 이 프로젝트는 `spring-boot-starter-web`이 아니라 세분화된 `spring-boot-starter-webmvc`를 쓰고 있다. 006/007 작업에서 이 구조 때문에 두 번이나 "기대한 자동 설정 빈이 없어서" 추가 의존성이 필요했던 적이 있다(JMeter 부하테스트 때 Actuator 관련, SMS 연동 때 `RestClient.Builder`). springdoc 추가 시에도 비슷한 문제가 날 수 있어서, 실제로 추가해서 기동까지 확인하기 전에는 "이거 하나면 끝"이라고 단정하지 않는다.
- 기존 Request/Response DTO(record)에는 Swagger 전용 `@Schema` 애노테이션이 없다 — 필드 하나하나에 설명을 달진 않고, 필드명/타입 기반 기본 스키마로 충분한지 확인한다.

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/config/OpenApiConfig.java
```

### 수정
```
pom.xml                                                              # springdoc-openapi-starter-webmvc-ui 추가
src/main/resources/application.properties                            # springdoc.paths-to-match=/api/** 등
src/main/java/com/example/board/security/SecurityConfig.java         # swagger 경로 permitAll 추가
src/main/java/com/example/board/controller/AdminController.java      # @Tag, @Operation 추가
src/main/java/com/example/board/controller/AuthController.java       # @Tag, @Operation 추가
src/main/java/com/example/board/controller/BoardController.java      # @Tag, @Operation 추가
src/main/java/com/example/board/controller/CommentController.java    # @Tag, @Operation 추가
src/main/java/com/example/board/controller/UserController.java       # @Tag, @Operation 추가
```

## 구현 방법

### 1. 의존성 추가
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

### 2. `OpenApiConfig`
```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        String bearerSchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("게시판 API").description("게시판 프로젝트 REST API 문서").version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
                .components(new Components().addSecuritySchemes(bearerSchemeName,
                        new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```
- 전역으로 `bearerAuth` 보안 스키마를 등록해서, 로그인 없이 호출 가능한 API(`/api/auth/**`, 게시글 목록 조회 등)에도 Authorize 버튼이 뜨지만 토큰 없이도 "Try it out"은 그대로 가능하다(Spring Security 쪽 permitAll 여부가 실제 인증 요구를 결정하고, Swagger의 보안 표시는 참고용이다).

### 3. `application.properties`
```properties
# Swagger (springdoc-openapi). /api/** 만 문서화 대상으로 포함 (MainController의 정적 페이지 라우팅 등 제외)
springdoc.paths-to-match=/api/**
```

### 4. `SecurityConfig`
`PERMIT_ALL_PATHS`에 `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` 추가.

### 5. 컨트롤러 애노테이션
각 컨트롤러 클래스에 `@Tag(name = "...")`, 각 엔드포인트 메서드에 `@Operation(summary = "...")` 추가. 태그는 다음과 같이 구분한다.

| 컨트롤러 | 태그명 |
|---|---|
| AuthController | 인증 |
| BoardController | 게시글 |
| CommentController | 댓글 |
| UserController | 회원(마이페이지) |
| AdminController | 관리자 |

## 데이터베이스 변경 사항
없음.

## API 변경 사항
기존 API의 동작/계약은 변경되지 않는다. 문서 조회용 경로(`/swagger-ui.html`, `/v3/api-docs`)만 새로 추가된다.

## 테스트 계획
- `./mvnw test`로 기존 테스트 전체 회귀 확인 (springdoc 추가로 인한 부작용이 없는지).
- 로컬에서 앱 기동 후 `/swagger-ui.html` 접속, 25개 API가 태그별로 정상적으로 보이는지, `MainController`의 `/login`은 안 보이는지 Playwright로 확인.
- 실제 로그인해서 받은 JWT 액세스 토큰을 Authorize 버튼에 넣고, 인증이 필요한 API(`GET /api/users/me` 등)를 Swagger UI의 "Try it out"으로 직접 호출해서 정상 응답이 오는지 확인.

## 작업 상태
- Completed

### 구현 결과와의 차이점
- Plan대로 `spring-boot-starter-restclient` 같은 추가 의존성 걱정을 했었는데, 이번엔 `springdoc-openapi-starter-webmvc-ui` 하나만 추가해서 바로 정상 동작했다. 006/007과 달리 이번엔 추가 자동 설정 의존성이 필요 없었다.
- 그 외 설계(`springdoc.paths-to-match=/api/**`, Bearer 보안 스킴, 컨트롤러별 태그/요약)는 계획대로 구현됐다.
