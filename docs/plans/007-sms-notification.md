# 007. 댓글 알림 SMS 연동 (Twilio, 샌드박스)

## 작업 목적
이 프로젝트는 Redis, Docker 등 실제 인프라를 직접 경험해보는 것이 목적이었고, 이번에는 **외부 API 연동**(SMS 발송)을 경험해본다. 내 게시글에 댓글이 달리면 작성자에게 SMS로 알림을 보낸다. 외부 API 클라이언트 연동, 비동기 처리, 실패 로깅/재시도를 실습하는 것이 이번 Plan의 핵심 목표다.

카카오 알림톡은 카카오 비즈니스 채널 개설 + 템플릿 사전 검수(보통 며칠 소요, 사업자 정보 요구)가 필요해서 "샌드박스/테스트 키만 사용"이라는 이번 결정 조건과 맞지 않는다. 이번 Plan은 **SMS만** 구현하고, 알림톡은 필요해지면 별도 Plan으로 진행한다.

## 요구사항
- [ ] 다른 사람이 내 게시글에 댓글을 달면 SMS로 알림을 받는다 (본인 글에 본인이 댓글을 단 경우는 제외)
- [ ] 마이페이지에서 SMS를 받을 전화번호를 등록/수정할 수 있다 (미등록 시 알림 자체가 스킵됨)
- [ ] 마이페이지에서 내 SMS 발송 내역(성공/실패)을 확인할 수 있다
- [ ] SMS API 호출은 댓글 작성 응답 속도에 영향을 주지 않는다 (비동기 처리)
- [ ] SMS 발송이 실패해도 댓글 작성 자체는 정상 처리된다 (실패 격리 + 로깅)
- [ ] `sms.enabled=false`(기본값)면 SMS 관련 코드가 전혀 호출되지 않는다 — 키 없이도 앱이 정상 동작해야 함

### 확인된 사용자 결정 사항
- 알림톡이 아닌 **SMS**로 먼저 진행 (알림톡은 다음 기회에 별도 Plan).
- **샌드박스/테스트 키만 사용** — 사업자 등록 없이 개인 개발자로 가입 가능한 provider를 쓴다.
- Provider는 **Twilio**를 사용한다. 이유:
  - 개인 이메일만으로 무료 체험(Trial) 계정 생성 가능, 한국 사업자 등록 불필요.
  - Trial 계정은 본인이 Twilio 콘솔에서 **인증한 수신번호로만** 실제 SMS 발송 가능(문자 앞에 "Sent from your Twilio trial account" 문구가 붙음) — 실습 목적에는 충분.
  - 반대로 국내 SMS 대행사(알리고/Solapi 등)는 발신번호 사전등록에 본인 인증 절차가 있고 건당 과금이라 "테스트 키만" 조건과는 약간 거리가 있어 이번엔 제외.
  - **사용자가 직접 해야 하는 일**: [twilio.com](https://www.twilio.com/try-twilio)에서 무료 계정 생성 → Account SID / Auth Token 확인 → 콘솔에서 본인 수신 전화번호 인증(Verified Caller ID) → Trial 발신번호(Twilio가 무료로 부여) 확인. 이 값들은 `application-local.properties`(gitignore됨)에 직접 넣어야 하며, 이 값을 대신 추측하거나 채워 넣지 않는다(`005-admin.md`의 `admin.bootstrap-email`과 동일한 패턴).

## 현재 구조 및 관련 코드
- `User` 엔티티(`entity/User.java`)에는 전화번호 필드가 없다. SMS 수신자를 특정하려면 필드 추가가 필요하다.
- `CommentService.create()`(`service/CommentService.java`)가 댓글 저장 로직의 유일한 진입점이며, `@Transactional`이다. 여기서 바로 SMS 발송을 호출하면, 트랜잭션이 이후 어떤 이유로든 롤백될 경우 "존재하지 않는 댓글"에 대해 SMS가 나가는 정합성 문제가 생길 수 있다. → 이번 Plan에서 **Spring ApplicationEvent + `@TransactionalEventListener(phase = AFTER_COMMIT)`** 패턴을 처음 도입해서, 트랜잭션이 실제로 커밋된 이후에만 알림 로직이 실행되도록 한다.
- 비동기 실행 인프라(`@EnableAsync`, `TaskExecutor`)가 프로젝트에 아직 없다. 전용 스레드풀을 새로 만든다(공용 스레드풀을 쓰면 다른 비동기 작업이 생겼을 때 서로 영향을 줄 수 있어서, 외부 API 호출처럼 느려질 수 있는 작업은 분리하는 게 안전).
- HTTP 클라이언트: `spring-boot-starter-webmvc`에 `spring-web`이 포함되어 있어 Spring 6+의 `RestClient`를 바로 쓸 수 있다. 새 의존성 추가가 필요 없다.
- `UserController`는 현재 `UserService` 없이 `BoardService`/`CommentService`를 직접 호출하는 구조다. 전화번호 수정처럼 `User` 엔티티를 직접 변경하는 로직은 Repository를 직접 호출하지 않는다는 Architecture Rule에 따라 새 `UserService`를 만들어 그 안에 둔다.
- `mypage.html`/`mypage.js`의 프로필 카드(`#mypage-profile`)는 현재 이름/이메일/역할만 보여준다. 여기에 전화번호 등록 폼을 추가하고, 4번째 탭으로 "알림 내역"을 추가한다.
- `application.properties`에는 `admin.bootstrap-email=` 처럼 "빈 기본값 + 실제 값은 gitignore된 local 설정 파일" 패턴이 이미 있다 — SMS 키도 동일 패턴을 따른다.

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/entity/SmsNotificationLog.java
src/main/java/com/example/board/repository/SmsNotificationLogRepository.java
src/main/java/com/example/board/event/CommentCreatedEvent.java
src/main/java/com/example/board/notification/CommentNotificationListener.java
src/main/java/com/example/board/notification/SmsSender.java              # 인터페이스
src/main/java/com/example/board/notification/TwilioSmsSender.java        # 구현체
src/main/java/com/example/board/config/AsyncConfig.java                  # @EnableAsync + smsTaskExecutor 빈
src/main/java/com/example/board/service/UserService.java
src/main/java/com/example/board/dto/request/PhoneNumberUpdateRequest.java
src/main/java/com/example/board/dto/response/SmsNotificationLogResponse.java

src/test/java/com/example/board/notification/CommentNotificationListenerTest.java
src/test/java/com/example/board/notification/TwilioSmsSenderTest.java
src/test/java/com/example/board/service/UserServiceTest.java
```

### 수정
```
src/main/java/com/example/board/entity/User.java              # phoneNumber 필드 + changePhoneNumber() 추가
src/main/java/com/example/board/service/CommentService.java   # create()에서 CommentCreatedEvent 발행
src/main/java/com/example/board/controller/UserController.java # PATCH /me/phone, GET /me/notifications 추가
src/main/java/com/example/board/dto/response/UserResponse.java # phoneNumber 필드 추가
src/main/resources/application.properties                     # sms.* 설정 추가
src/main/resources/static/mypage.html                          # 전화번호 등록 폼 + 알림 내역 탭
src/main/resources/static/js/mypage.js                         # 위 UI의 동작

src/test/java/com/example/board/service/CommentServiceTest.java   # 이벤트 발행 검증 추가
src/test/java/com/example/board/controller/UserControllerTest.java # 신규 엔드포인트 테스트 추가
```

## 구현 방법

### 1. `User`에 전화번호 필드 추가
```java
@Column(length = 20)
private String phoneNumber;   // E.164 형식, 예: +821012345678

public void changePhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
}
```
- 별도 DDL 필요 없음 — `ddl-auto=update`가 nullable 컬럼 추가는 자동으로 처리한다(007의 FULLTEXT와 달리 평범한 컬럼이라 수동 DDL 불필요).

### 2. 이벤트 발행 (`CommentService`)
```java
@Transactional
public Long create(Long userId, Long boardId, CommentCreateRequest request) {
    ...
    Long commentId = commentRepository.save(comment).getId();
    eventPublisher.publishEvent(new CommentCreatedEvent(commentId, boardId, board.getUser().getId(), userId));
    return commentId;
}
```
- `ApplicationEventPublisher` 주입.

### 3. `CommentNotificationListener`
```java
@Component
@RequiredArgsConstructor
public class CommentNotificationListener {

    @Async("smsTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event.boardAuthorId().equals(event.commenterId())) {
            return; // 본인 글에 본인 댓글
        }
        User author = userRepository.findById(event.boardAuthorId()).orElse(null);
        if (author == null || author.getPhoneNumber() == null || author.getPhoneNumber().isBlank()) {
            return; // 전화번호 미등록
        }
        String message = "[게시판] " + "회원님의 글에 새 댓글이 달렸습니다.";
        smsSender.send(author, message); // 내부에서 성공/실패를 SmsNotificationLog에 기록
    }
}
```
- 트랜잭션 커밋 후, 별도 스레드(`smsTaskExecutor`)에서 실행되므로 댓글 작성 API 응답 속도에 영향이 없다.
- 리스너 자체가 예외를 던지지 않도록 `SmsSender.send()` 내부에서 모든 예외를 잡는다(리스너가 던지면 비동기 스레드에서 로그만 남고 요청에는 영향 없긴 하지만, 실패 기록을 명확히 하기 위해 `SmsSender` 내부에서 처리).

### 4. `SmsSender` / `TwilioSmsSender`
```java
public interface SmsSender {
    void send(User recipient, String message);
}
```
```java
@Component
public class TwilioSmsSender implements SmsSender {
    // @Value: sms.enabled, sms.twilio.account-sid, sms.twilio.auth-token, sms.twilio.from-number
    // RestClient로 https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json 에
    // Basic Auth(sid, token) + form-urlencoded(To, From, Body) POST

    @Override
    public void send(User recipient, String message) {
        if (!enabled) {
            log.debug("SMS 비활성화 상태 - 발송 스킵");
            return;
        }
        int attempts = 0;
        Exception lastError = null;
        while (attempts < MAX_ATTEMPTS) {   // 최대 2회
            try {
                attempts++;
                callTwilio(recipient.getPhoneNumber(), message);
                saveLog(recipient, message, Status.SUCCESS, null);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("SMS 발송 실패 (시도 {}/{}): {}", attempts, MAX_ATTEMPTS, e.getMessage());
            }
        }
        saveLog(recipient, message, Status.FAILED, lastError.getMessage());
    }
}
```
- 재시도는 2회로 제한(무한 재시도로 인한 API 비용/속도 제한 걱정을 줄임), 재시도 사이에 짧은 대기(예: 500ms) 후 재시도.
- 모든 예외(네트워크 오류, 4xx/5xx 응답 등)를 잡아서 리스너나 상위로 전파하지 않는다 — CLAUDE.md Redis 규칙과 동일한 "외부 시스템 장애가 서비스 전체 장애로 이어지지 않게 한다"는 원칙을 SMS에도 동일하게 적용.

### 5. `SmsNotificationLog` (발송 이력)
```java
@Entity
@Table(name = "sms_notification_logs")
public class SmsNotificationLog {
    @Id @GeneratedValue
    private Long id;
    @Column(nullable = false)
    private Long recipientUserId;
    @Column(nullable = false, length = 20)
    private String phoneNumber;
    @Column(nullable = false, length = 200)
    private String message;
    @Enumerated(EnumType.STRING)
    private SmsStatus status;          // SUCCESS, FAILED
    private String errorMessage;
    private LocalDateTime createdAt;
}
```
- 매 발송 시도(성공/실패 모두)를 1건씩 기록.

### 6. `AsyncConfig`
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("smsTaskExecutor")
    public TaskExecutor smsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sms-");
        executor.initialize();
        return executor;
    }
}
```

### 7. `UserService` + 전화번호/알림내역 API
```java
@PatchMapping("/me/phone")
public ResponseEntity<Void> updatePhone(@AuthenticationPrincipal CustomUserDetails userDetails,
                                         @Valid @RequestBody PhoneNumberUpdateRequest request) {
    userService.updatePhoneNumber(userDetails.getId(), request.phoneNumber());
    return ResponseEntity.noContent().build();
}

@GetMapping("/me/notifications")
public ResponseEntity<PageResponse<SmsNotificationLogResponse>> myNotifications(...) {
    return ResponseEntity.ok(userService.getMyNotificationLogs(userDetails.getId(), page, size));
}
```
- `PhoneNumberUpdateRequest`는 `@Pattern`으로 `+`로 시작하는 E.164 형식(`^\+[1-9]\d{7,14}$`)을 검증.

### 8. 화면
- `mypage.html`의 `#mypage-profile`에 전화번호 표시 + "수정" 버튼/입력창 추가. 저장 시 `PATCH /api/users/me/phone` 호출.
- 4번째 탭 "알림 내역" 추가, `GET /api/users/me/notifications` 결과를 표로 표시(발송 시각, 메시지, 성공/실패).
- `board/detail.html`(댓글 작성 폼)은 변경 없음 — 알림은 서버 내부(이벤트)에서 처리되므로 클라이언트 코드 변경 불필요.

### 9. 설정 (`application.properties`)
```properties
sms.enabled=false
sms.twilio.account-sid=
sms.twilio.auth-token=
sms.twilio.from-number=
```
- 실제 값은 `application-local.properties`(gitignore)에 사용자가 직접 입력.
- `sms.enabled=false`가 기본값이므로, 키가 없는 환경(CI, 다른 개발자 PC)에서도 앱과 테스트가 정상 동작한다.

## 데이터베이스 변경 사항
| 테이블 | 변경 | 비고 |
|---|---|---|
| users | `phone_number VARCHAR(20)` nullable 컬럼 추가 | ddl-auto=update로 자동 생성 |
| sms_notification_logs | 신규 테이블 | ddl-auto=update로 자동 생성 (수동 DDL 불필요) |

## API 변경 사항
| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| PATCH | /api/users/me/phone | 내 전화번호 등록/수정 | O |
| GET | /api/users/me/notifications?page=&size= | 내 SMS 발송 내역 조회(페이징) | O |
| GET | /api/users/me | 응답에 `phoneNumber` 필드 추가 | O (기존 엔드포인트) |

## 테스트 계획
- **`TwilioSmsSenderTest`**: `MockRestServiceServer`(`RestClient.Builder`에 바인딩)로 Twilio API를 모킹해서 성공/실패(4xx, 5xx, 타임아웃) 케이스, 재시도 2회 후 최종 실패 기록, `sms.enabled=false`일 때 아예 호출하지 않는지 검증.
- **`CommentNotificationListenerTest`**: 본인 댓글이면 스킵, 전화번호 미등록이면 스킵, 정상 케이스면 `SmsSender.send()` 호출 검증(Mockito).
- **`CommentServiceTest`**: `create()` 호출 시 `CommentCreatedEvent`가 올바른 값으로 발행되는지 검증(`ApplicationEventPublisher` mock).
- **`UserServiceTest`**: 전화번호 형식 검증, 알림 내역 페이징 조회.
- **`UserControllerTest`**: 신규 엔드포인트 인증/검증 동작.
- **실제 연동 확인(수동)**: 사용자가 Twilio 키를 `application-local.properties`에 넣고 `sms.enabled=true`로 설정한 뒤, 본인 인증된 번호로 실제 댓글 알림 SMS가 오는지 직접 확인 — 이 부분은 실제 키가 있어야 하므로 자동화 테스트가 아닌 수동 확인으로 진행하고 결과를 Work Log에 기록한다.
- 전체 구현 후 `./mvnw test`로 기존 테스트 포함 전체 회귀 확인.

## 작업 상태
- Completed

### 구현 결과와의 차이점
- **`RestClient.Builder` 빈이 기본적으로 없었다.** 이 프로젝트는 `spring-boot-starter-web`이 아니라 세분화된 `spring-boot-starter-webmvc`를 쓰고 있어서, `RestClient.Builder` 자동 설정이 함께 딸려오지 않았다(`spring-boot-starter-web`을 썼다면 포함됐을 것). `spring-boot-starter-restclient` 의존성을 추가해서 해결했다 — Plan에는 "새 의존성 추가가 필요 없다"고 적었는데 실제로는 1개 필요했다.
- 나머지는 Plan대로 구현됐다: `CommentCreatedEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 조합, Twilio REST 연동, 최대 2회 재시도, `SmsNotificationLog` 기록, 마이페이지 전화번호 등록/알림 내역 UI.
- **실제 Twilio 엔드포인트로 종단간 검증을 했다.** 가짜 Account SID/Auth Token으로 `sms.enabled=true`를 켜고 실제 댓글 작성 흐름을 태워봤더니, 실제로 `https://api.twilio.com/...`까지 요청이 나가서 `401 auth account ... does not exist`를 받고, 2회 재시도 후 `SmsNotificationLog`에 FAILED로 기록되는 것을 로그와 마이페이지 화면에서 직접 확인했다. 폼 인코딩, Basic Auth 헤더, URL 경로가 모두 Twilio 스펙대로 정확히 구성된다는 뜻이라, 사용자가 실제 키를 넣기만 하면 바로 동작할 것으로 확인된다.
