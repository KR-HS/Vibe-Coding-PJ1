# 007. 댓글 알림 SMS 연동 - Work Log

## 2026-09-29

### 작업 내용
`docs/plans/007-sms-notification.md`에 따라, 내 게시글에 댓글이 달리면 Twilio SMS로 알림을 보내는 기능을 구현했다. 원래 007은 "게시글 검색 고도화"였으나 사용자가 Redis/Docker에 이어 외부 API 연동을 경험해보고 싶다고 방향을 바꾸면서, 검색 Plan은 취소하고 이 번호를 SMS 알림 Plan으로 재사용했다.

### 변경된 파일

**신규**
```
docs/plans/007-sms-notification.md
src/main/java/com/example/board/entity/SmsStatus.java
src/main/java/com/example/board/entity/SmsNotificationLog.java
src/main/java/com/example/board/repository/SmsNotificationLogRepository.java
src/main/java/com/example/board/event/CommentCreatedEvent.java
src/main/java/com/example/board/notification/SmsSender.java
src/main/java/com/example/board/notification/TwilioSmsSender.java
src/main/java/com/example/board/notification/CommentNotificationListener.java
src/main/java/com/example/board/config/AsyncConfig.java
src/main/java/com/example/board/service/UserService.java
src/main/java/com/example/board/dto/request/PhoneNumberUpdateRequest.java
src/main/java/com/example/board/dto/response/SmsNotificationLogResponse.java

src/test/java/com/example/board/notification/TwilioSmsSenderTest.java
src/test/java/com/example/board/notification/CommentNotificationListenerTest.java
src/test/java/com/example/board/service/UserServiceTest.java
```

**수정**
```
pom.xml                                                            # spring-boot-starter-restclient 추가
src/main/java/com/example/board/entity/User.java                   # phoneNumber 필드 + changePhoneNumber()
src/main/java/com/example/board/service/CommentService.java        # create()에서 CommentCreatedEvent 발행
src/main/java/com/example/board/controller/UserController.java     # PATCH /me/phone, GET /me/notifications 추가
src/main/java/com/example/board/dto/response/UserResponse.java     # phoneNumber 필드 추가
src/main/resources/application.properties                          # sms.* 설정 추가 (기본 비활성화)
src/main/resources/static/mypage.html                               # 전화번호 등록 폼 + 알림 내역 탭
src/main/resources/static/js/mypage.js                              # 위 UI 동작
src/main/resources/static/css/style.css                             # phone-edit-form, sms-status 배지 스타일

src/test/java/com/example/board/controller/AuthControllerTest.java  # UserResponse 생성자에 phoneNumber 인자 추가(컴파일 대응)
src/test/java/com/example/board/controller/UserControllerTest.java  # 신규 엔드포인트 테스트 추가
src/test/java/com/example/board/service/CommentServiceTest.java     # 이벤트 발행 검증 테스트 추가
```

### 주요 변경사항
- `CommentService.create()`가 댓글 저장 직후 `CommentCreatedEvent`를 발행하고, `CommentNotificationListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("smsTaskExecutor")`로 받아서 처리한다 — 댓글 트랜잭션이 실제로 커밋된 뒤에만, 그리고 별도 스레드에서 실행되므로 댓글 작성 응답 속도에 영향이 없다.
- `TwilioSmsSender`가 Spring `RestClient`로 Twilio Programmable SMS API(`POST /Accounts/{sid}/Messages.json`, Basic Auth, form-urlencoded)를 호출한다. 실패 시 최대 2회(재시도 1회) 시도하고, 최종 성공/실패를 `SmsNotificationLog`에 기록한다. 모든 예외를 내부에서 잡아서 상위(댓글 작성 요청)로 전파하지 않는다 — CLAUDE.md의 Redis 장애 격리 원칙과 동일하게 적용.
- `sms.enabled=false`가 기본값이라 키가 없는 환경(CI, 다른 개발자 PC)에서도 앱/테스트가 정상 동작한다. 실제 값은 `application-local.properties`(gitignore)에 사용자가 직접 넣어야 하며, 이 키를 대신 채워 넣지 않았다.
- 마이페이지에 전화번호 등록/수정 폼과 "알림 내역" 탭을 추가해서, 기능이 API로만 끝나지 않고 실제로 확인 가능한 화면을 갖추도록 했다.

### 테스트 결과
- `./mvnw test` 전체 100건 통과(BUILD SUCCESS) — 기존 87건 + 신규 13건(`TwilioSmsSenderTest` 3, `CommentNotificationListenerTest` 4, `UserServiceTest` 3, `CommentServiceTest`/`UserControllerTest` 추가분 3).
- `TwilioSmsSenderTest`는 `MockRestServiceServer`를 `RestClient.Builder`에 바인딩해서 Twilio API를 모킹, 성공/재시도 후 실패/비활성화 스킵 케이스를 검증했다.
- **실제 Twilio 엔드포인트로 종단간(E2E) 검증**: 로컬에서 앱을 `-Dsms.enabled=true -Dsms.twilio.account-sid=AC0000...(가짜)`로 띄우고, 테스트 계정 2개(작성자/댓글러)로 실제 회원가입 → 작성자 전화번호 등록 → 게시글 작성 → 댓글 작성까지 curl로 재현했다. 결과:
  - `PATCH /api/users/me/phone` → 204, `GET /api/users/me` 응답에 저장된 번호가 그대로 조회됨.
  - 댓글 작성 후 약 1초 뒤, 앱 로그에 실제로 `https://api.twilio.com/2010-04-01/Accounts/.../Messages.json`으로 요청이 나가서 `401 auth account ... does not exist`(가짜 SID라 당연한 결과)를 받고 2회 재시도 후 `SmsNotificationLog`에 FAILED로 기록되는 것을 확인.
  - Playwright로 실제 브라우저에서 마이페이지에 접속해 전화번호 입력창에 저장된 번호가 그대로 표시되고, "알림 내역" 탭에 방금 실패한 발송 건(시각/메시지/"발송 실패" 배지)이 정확히 표시되는 것을 스크린샷으로 확인.
  - 검증에 사용한 테스트 계정 2명, 게시글 2건(중간에 응답 파싱 실수로 board가 한 번 더 생성됨), 댓글 1건, SMS 로그 1건은 모두 확인 후 직접 생성한 것만 골라서 삭제했다(실제 사용자 데이터는 건드리지 않음).
- 본인 댓글(자기 글에 자기가 댓글) 스킵, 전화번호 미등록 스킵, 작성자 없음 스킵은 `CommentNotificationListenerTest`로 단위 테스트했다(실제 SMS 호출까지는 확인하지 않아도 되는 로직이라 E2E로 재검증하지 않음).

### 발생한 문제 및 해결 방법
1. **`RestClient.Builder` 빈이 없어서 앱 컨텍스트 로딩 자체가 실패했다.** 이 프로젝트는 `spring-boot-starter-web`이 아니라 `spring-boot-starter-webmvc`(세분화된 Spring Boot 4 스타터)를 쓰고 있어서, `RestClient.Builder` 자동 설정을 제공하는 `spring-boot-starter-restclient`가 딸려오지 않았다. `pom.xml`에 이 의존성을 추가해서 해결 — `mvn test`로 전체 스위트를 돌려보기 전까지는 컴파일은 되지만 컨텍스트 로딩 시점에야 드러나는 문제였다.
2. **로컬 검증 중 curl로 한글 이름을 포함한 JSON을 보내면 서버가 500을 반환했다.** 원인은 SMS 기능과 무관하게 Windows Git Bash에서 `curl -d`로 멀티바이트 문자열을 보낼 때 쉘 인코딩이 깨지는 문제였다(이전 세션에서 `application.properties` 한글 주석이 깨졌던 것과 같은 종류의 환경 문제). 테스트 계정 이름을 ASCII로 바꿔서 우회했고, 실제 앱 버그는 아니었다.
3. **게시글 생성 API가 `multipart/form-data`를 요구하는 걸 깜빡하고 처음에 `application/json`으로 보내서 415가 났다.** `BoardController.create()`가 첨부파일 지원 때문에 `@RequestPart`를 쓰는 걸 확인하고 `curl -F`로 재시도해서 해결 — 앱 코드 문제 아님.

### Plan과 실제 구현의 차이점
`docs/plans/007-sms-notification.md`의 "구현 결과와의 차이점" 절 참고. 핵심은 `spring-boot-starter-restclient` 의존성이 예상과 달리 추가로 필요했다는 점이고, 그 외 설계(이벤트 기반 비동기 처리, 재시도, 실패 격리, UI)는 계획대로 구현됐다.
