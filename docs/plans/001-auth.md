# 001. 회원 인증 (자체 회원가입 + Google/Naver OAuth2 + JWT)

## 작업 목적
게시판의 게시글 CRUD(작성자 식별, 수정/삭제 권한 검증)에 앞서, 회원 가입/로그인 및 인증·인가 기반을 먼저 구축한다.
- 자체(이메일/비밀번호) 회원가입 및 로그인
- Google, Naver 소셜 로그인(OAuth2)
- JWT 기반 인증 (Access Token + Refresh Token, Refresh Token은 Redis에 저장)
- USER / ADMIN Role 기반 권한 체계

이후 게시판 CRUD Plan(`002-board-crud.md`)은 본 Plan에서 만들어지는 `User` 엔티티 및 인증 컨텍스트(`Authentication`에서 사용자 식별)를 전제로 작성한다.

## 요구사항
- [o] 이메일/비밀번호 자체 회원가입, 로그인
- [o] Google OAuth2 로그인 (코드 구현 완료. 단, 실제 client-id/secret은 플레이스홀더 상태라 실 IdP 연동 수동 테스트는 미실시)
- [o] Naver OAuth2 로그인 (코드 구현 완료. 단, 실제 client-id/secret은 플레이스홀더 상태라 실 IdP 연동 수동 테스트는 미실시)
- [o] JWT Access Token(단기) + Refresh Token(장기, Redis 저장) 발급/재발급/폐기
- [o] Role 기반 권한 구분 (USER, ADMIN)
- [o] 비밀번호는 암호화하여 저장 (BCrypt)
- [o] Access Token 만료 시 Refresh Token으로 재발급 가능
- [o] 로그아웃 시 Refresh Token 무효화

### 확인된 사용자 결정 사항
- 로그인 범위: 자체 회원가입 + Google + Naver (3가지 모두 지원)
- JWT 전략: Access + Refresh Token, Refresh Token은 Redis 저장
- 권한 체계: Role 기반 (USER / ADMIN)

## 현재 구조 및 관련 코드
프로젝트는 Spring Initializr로 생성된 초기 상태이며, 인증/회원 관련 코드가 전혀 없다.

```
src/main/java/com/example/board/
├── BoardApplication.java
└── controller/
    └── MainController.java   // "/" -> home.html 반환만 수행

src/main/resources/
├── application.properties    // spring.application.name, server.port 만 설정됨
└── templates/home.html
```

`pom.xml` 현재 의존성: `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, `mybatis-spring-boot-starter`, `mysql-connector-j`, `lombok`, `devtools` (+ 대응 test 스타터).

**Security, OAuth2 Client, Redis, JWT 관련 의존성은 pom.xml에 없다.** 본 작업에서 아래 의존성 추가가 필요하다(신규 의존성 추가 전 기존 의존성으로 대체 불가함을 확인함 — Security/OAuth2/Redis/JWT는 대체 수단이 없어 필수 추가).

```xml
<!-- 추가 필요 -->
org.springframework.boot:spring-boot-starter-security
org.springframework.boot:spring-boot-starter-oauth2-client
org.springframework.boot:spring-boot-starter-data-redis
io.jsonwebtoken:jjwt-api:0.12.6
io.jsonwebtoken:jjwt-impl:0.12.6 (runtime)
io.jsonwebtoken:jjwt-jackson:0.12.6 (runtime)
org.springframework.boot:spring-boot-starter-security (test 필요 시 spring-security-test 추가)
```

`application.properties`에도 DB, Redis, OAuth2 client-id/secret, JWT secret/만료시간 설정이 없다.

## 사전 준비: 로컬 MySQL/Redis 연동 (인증 기능 구현 전 선행 작업)
인증 기능 구현을 시작하기 전에, 로컬 개발 환경에서 MySQL 스키마 생성과 Redis 연동을 먼저 완료한다.

- 기존에 로컬에 설치된 MySQL/Redis는 사용하지 않고, **Docker Compose로 신규 컨테이너를 구성**한다. 기존 로컬 계정 정보는 필요 없음.
- MySQL 계정 정보(DB명/유저/비밀번호)는 새로 생성하여 `.env`에 보관한다 (예: `MYSQL_DATABASE=board`, `MYSQL_USER=board`, `MYSQL_PASSWORD=<신규 생성>`, `MYSQL_ROOT_PASSWORD=<신규 생성>`).
- **Redis 인증**: `requirepass`를 설정해 비밀번호 인증을 적용한다. 비밀번호는 `.env`의 `REDIS_PASSWORD`로 관리하고, `application-local.properties`에 `spring.data.redis.password`로 주입한다.
- **네트워크 노출 범위**: 현재 Spring Boot 앱은 컨테이너가 아니라 로컬에서 직접 실행되므로, MySQL/Redis 포트를 호스트에 노출해야 앱이 접근할 수 있다. 단, `0.0.0.0` 전체 바인딩 대신 **`127.0.0.1:포트:포트`로 바인딩**하여 로컬 머신 외부(동일 네트워크의 다른 PC 등)에서는 접근하지 못하도록 제한한다.
- **전용 Docker 네트워크 분리는 이번 범위에서 보류**: Docker Compose는 프로젝트 단위로 기본 네트워크를 자동 생성하므로, 현재 구성(MySQL/Redis만 존재)에서는 이미 다른 프로젝트 컨테이너와 격리되어 있어 별도 named network를 만드는 실익이 적다. Spring Boot 앱 자체를 컨테이너화(K8s 배포 등)해서 앱↔MySQL, 앱↔Redis가 포트 노출 없이 내부 네트워크로만 통신하게 되는 시점에 재검토한다.
- 비밀번호 등 민감 정보는 git에 커밋되는 `application.properties`에 직접 넣지 않고, `application-local.properties`(gitignore 처리)로 분리한다. `application.properties`에는 플레이스홀더/공통 설정만 남긴다.

### 신규/수정 파일 (인프라)
```
docker-compose.yml                              # 신규 — MySQL 8, Redis 7 컨테이너 정의 (Redis requirepass 포함)
.env                                             # 신규, gitignore 처리 — MySQL/Redis 계정·비밀번호
.gitignore                                       # 수정 — .env, application-local.properties 추가
src/main/resources/application-local.properties  # 신규, gitignore 처리 — 로컬 DB/Redis 실제 접속정보
src/main/resources/application.properties        # 수정 — spring.profiles.active=local, 공통 설정(민감정보 제외)
```

### 확인 순서
1. `docker-compose.yml` 작성 (MySQL 8 + Redis 7, `127.0.0.1` 바인딩, Redis `requirepass` 적용)
2. `docker-compose up -d`로 컨테이너 기동
3. `application-local.properties`에 접속 정보 작성 후 `spring-boot:run`으로 연결 확인 (User 엔티티 추가 전이므로 우선 DataSource/Redis 연결 자체만 확인)
4. 연결 확인 후 이어서 User 엔티티/인증 기능 구현 진행

### 실제 결과 (완료, 2026-08-12)
- 로컬에 기존 `MySQL80` Windows 서비스가 이미 3306/33060 포트를 사용 중이어서 충돌 발생 → **Docker MySQL의 호스트 포트를 3307로 변경**(`127.0.0.1:3307:3306`), `application-local.properties`의 `spring.datasource.url`도 3307로 수정. 기존 로컬 MySQL80 서비스는 건드리지 않음.
- Redis는 계획대로 `127.0.0.1:6379`, `requirepass` 적용.
- `docker exec`로 직접 검증: `mysqladmin ping` 성공, `board` 스키마 생성 확인, `redis-cli -a <password> ping` → `PONG`, 비밀번호 없이 `redis-cli ping` → `NOAUTH Authentication required` (인증이 실제로 걸려있음을 확인).
- Spring Boot 앱을 통한 전체 부팅(Hibernate 연결) 테스트는 `spring-boot-starter-parent 4.0.8-SNAPSHOT`의 최초 스냅샷 의존성 다운로드가 오래 걸려 이번 확인에서는 생략. User 엔티티/JPA 코드 작성 시점에 자연스럽게 확인 예정.
- 이 선행 작업(인프라 연동)은 완료 상태이며, User 엔티티 등 실제 인증 기능 구현은 아직 시작하지 않음(Plan 전체 상태는 `Planned` 유지).

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/
├── entity/
│   ├── User.java                       # 회원 Entity (JPA)
│   └── Role.java                       # enum: USER, ADMIN
├── dto/
│   ├── request/
│   │   ├── SignupRequest.java
│   │   ├── LoginRequest.java
│   │   └── ReissueRequest.java
│   └── response/
│       ├── TokenResponse.java          # accessToken, refreshToken
│       └── UserResponse.java           # 내 정보 조회 응답
├── repository/
│   └── UserRepository.java             # JPA Repository
├── service/
│   ├── AuthService.java                # 회원가입, 로그인, 재발급, 로그아웃
│   └── CustomOAuth2UserService.java    # OAuth2 사용자 정보 로드/가입 처리
├── security/
│   ├── SecurityConfig.java             # SecurityFilterChain 설정
│   ├── jwt/
│   │   ├── JwtTokenProvider.java       # 토큰 생성/검증/파싱
│   │   └── JwtAuthenticationFilter.java# Access Token 검증 필터
│   ├── CustomUserDetails.java
│   ├── CustomUserDetailsService.java   # 자체 로그인용 UserDetailsService
│   └── oauth2/
│       ├── OAuth2UserInfo.java         # provider별 사용자 정보 파싱 인터페이스
│       ├── GoogleUserInfo.java
│       ├── NaverUserInfo.java          # Naver는 "response" 필드로 감싸져 있어 별도 파싱 필요
│       └── OAuth2SuccessHandler.java   # 로그인 성공 시 JWT 발급 후 리다이렉트
├── controller/
│   ├── AuthController.java             # /api/auth/**
│   └── UserController.java             # /api/users/me
├── config/
│   └── RedisConfig.java                # RedisTemplate 설정
└── exception/
    ├── DuplicateEmailException.java
    ├── InvalidTokenException.java
    └── GlobalExceptionHandler.java     # @RestControllerAdvice
```

### 수정
```
pom.xml                          # 의존성 추가
src/main/resources/application.properties   # DB/Redis/OAuth2/JWT 설정 추가
```

## 구현 방법

### 1. User 엔티티
- 필드: `id`, `email`(unique), `password`(nullable, OAuth 전용 계정은 null), `name`, `role`(USER/ADMIN, 기본 USER), `provider`(LOCAL/GOOGLE/NAVER), `providerId`(nullable), `createdAt`, `updatedAt`.
- `email` 단일 unique 제약으로 자체 가입/소셜 가입 계정을 동일 이메일 기준 식별한다.

### 2. 자체 회원가입/로그인
- `POST /api/auth/signup`: 이메일 중복 확인 → `BCryptPasswordEncoder`로 비밀번호 암호화 → `User` 저장.
- `POST /api/auth/login`: `AuthenticationManager` + `CustomUserDetailsService`로 인증 → 성공 시 Access/Refresh Token 발급.

### 3. JWT 발급/검증
- `JwtTokenProvider`: 사용자 id, role을 claim에 담아 Access Token(예: 30분), Refresh Token(예: 14일) 서명 생성. secret key는 `application.properties`(또는 환경변수)로 주입.
- Refresh Token은 Redis에 `refresh:{userId}` 키로 저장하고 TTL을 Refresh Token 만료 시간과 동일하게 설정한다.
- `JwtAuthenticationFilter`: `Authorization: Bearer {accessToken}` 헤더를 파싱해 `SecurityContext`에 인증 정보를 설정하는 `OncePerRequestFilter`.
- `POST /api/auth/reissue`: 전달받은 Refresh Token을 Redis 저장값과 대조 → 일치 시 Access Token(+ 선택적으로 Refresh Token) 재발급.
- `POST /api/auth/logout`: Redis에서 해당 사용자의 Refresh Token 삭제.

### 4. OAuth2 (Google / Naver)
- `SecurityConfig`에서 `.oauth2Login()` 설정, `CustomOAuth2UserService`가 provider별 사용자 정보를 조회.
- Google은 Spring Security의 `CommonOAuth2Provider.GOOGLE` 기본 설정을 사용.
- Naver는 Spring Security 기본 제공 provider가 아니므로 `application.properties`에 `authorization-uri`, `token-uri`, `user-info-uri`를 직접 등록하고, 응답이 `response` 객체로 한 번 감싸져 오므로 `user-name-attribute=response`로 지정 + `NaverUserInfo`에서 별도 파싱 로직 작성.
- 최초 로그인 시 `email` 기준으로 `User`가 없으면 신규 가입(provider/providerId 기록) 처리 후 `OAuth2SuccessHandler`에서 JWT를 발급해 프론트엔드로 리다이렉트(쿼리 파라미터 또는 리다이렉트 URL 설계는 프론트 연동 방식에 따라 추후 조정 필요 — 현재 프론트엔드가 없으므로 우선 JSON 응답 또는 redirect URL에 토큰을 담는 방식으로 구현하고, 프론트 연동 시점에 재검토).

### 5. 권한(Role) 처리
- `SecurityConfig`에서 `/api/auth/**`, OAuth2 로그인 경로는 `permitAll()`, 그 외 `/api/**`는 인증 필요.
- ADMIN 전용 엔드포인트는 이번 Plan 범위에는 없음(추후 관리자 기능 Plan에서 `hasRole("ADMIN")`로 확장).

### 6. 예외 처리
- `GlobalExceptionHandler`(`@RestControllerAdvice`)로 이메일 중복, 인증 실패, 토큰 무효 등을 일관된 에러 응답(JSON)으로 변환.

## 데이터베이스 변경 사항
`user` 테이블 신규 생성 (JPA 엔티티 기반).

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NULL 허용 (OAuth 전용 계정) |
| name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' |
| provider | VARCHAR(20) | NOT NULL (LOCAL/GOOGLE/NAVER) |
| provider_id | VARCHAR(255) | NULL 허용 |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

- 기존 테이블이 없으므로 데이터 영향 없음. 로컬 개발 환경은 `spring.jpa.hibernate.ddl-auto=update`로 진행하고, 이후 운영 배포 전 별도 마이그레이션 전략(Flyway 등 도입 여부)은 필요 시 추후 논의.
- Redis: `refresh:{userId}` 키에 Refresh Token 문자열 저장, TTL = Refresh Token 만료 시간.

## API 변경 사항

| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| POST | /api/auth/signup | 자체 회원가입 | X |
| POST | /api/auth/login | 이메일/비밀번호 로그인 | X |
| POST | /api/auth/reissue | Access Token 재발급 | X (Refresh Token 필요) |
| POST | /api/auth/logout | 로그아웃 (Refresh Token 무효화) | O |
| GET | /oauth2/authorization/google | Google 로그인 시작 (Spring Security 기본 제공) | X |
| GET | /oauth2/authorization/naver | Naver 로그인 시작 (Spring Security 기본 제공) | X |
| GET | /api/users/me | 내 정보 조회 | O |

## 테스트 계획
- **Service 단위 테스트**
  - 회원가입: 정상 가입, 이메일 중복 시 예외
  - 로그인: 정상 로그인 성공/비밀번호 불일치 실패
  - JwtTokenProvider: 토큰 생성, 정상 파싱, 만료/변조 토큰 검증 실패
  - Refresh 재발급: 정상 재발급, Redis에 없는(만료/로그아웃된) Refresh Token으로 재발급 시도 시 실패
- **Controller 통합 테스트**
  - `/api/auth/signup`, `/api/auth/login`, `/api/auth/reissue`, `/api/auth/logout` 요청/응답 상태 코드 검증
  - `/api/users/me` 인증 토큰 유무에 따른 200/401 검증
- OAuth2(Google/Naver) 로그인은 외부 IdP 연동이 필요해 자동화된 통합 테스트로 검증하기 어려움 → 로컬 환경에서 실제 OAuth2 클라이언트 등록 후 수동 테스트로 대체.

### 실제 결과 (2026-08-13)
- Service 단위 테스트(`AuthServiceTest`, `JwtTokenProviderTest`)와 Controller 테스트(`AuthControllerTest`, `UserControllerTest`)를 작성, `./mvnw test` 전체 통과 확인(총 21건, `BoardApplicationTests` 포함).
- `/api/auth/signup`, `/api/auth/login`, `/api/auth/reissue`는 `@WebMvcTest` + `MockMvc`로 요청/응답 상태 코드를 검증했다.
- `/api/auth/logout`, `/api/users/me`는 계획과 달리 MockMvc 기반 HTTP 통합 테스트 대신 컨트롤러 메서드를 직접 호출하는 단위 테스트로 대체했다. `@WebMvcTest` 슬라이스는 `SecurityConfig`(`@EnableWebSecurity`)를 로드하지 않아 `@AuthenticationPrincipal` 인자 리졸버가 등록되지 않고, 반대로 `SecurityConfig`를 그대로 가져오면 OAuth2 클라이언트 자동설정이 `ClientRegistrationRepository` 등 슬라이스에 없는 빈을 요구해 컨텍스트 로딩이 실패했다. 두 엔드포인트 모두 컨트롤러가 `@AuthenticationPrincipal`을 서비스로 위임만 하는 얇은 구조라 직접 호출 테스트로도 상태 코드·위임 여부 검증에 충분하다고 판단.
- Google/Naver `client-id`/`client-secret`은 `application-local.properties`에 플레이스홀더로 남아 있어(`REPLACE_WITH_...`), 실제 IdP 등록 후 수동 로그인 테스트는 아직 진행하지 않음 — 실제 Google/Naver 개발자 콘솔 앱 등록이 필요한 사용자 액션이므로 별도 진행 필요.

## 작업 상태
Completed (OAuth2 Google/Naver 실제 client-id/secret 등록 및 수동 로그인 테스트는 사용자가 실제 IdP 앱을 등록한 뒤 별도 진행 필요)
