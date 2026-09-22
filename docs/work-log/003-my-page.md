# 003. 마이페이지 (내가 쓴 글/댓글 조회) - Work Log

## 2026-09-21

### 작업 내용
`docs/plans/003-my-page.md`에 따라 마이페이지(API+화면)를 구현했다. 내가 쓴 게시글/댓글을 조회 전용으로 모아볼 수 있으며, 항목 클릭 시 기존 게시글 상세 화면으로 이동한다.

### 변경된 파일

**신규**
```
src/main/java/com/example/board/dto/response/MyCommentResponse.java
src/main/resources/static/mypage.html
src/main/resources/static/js/mypage.js
```

**수정**
```
src/main/java/com/example/board/controller/UserController.java     # GET /api/users/me/boards, /api/users/me/comments 추가
src/main/java/com/example/board/service/BoardService.java          # getMyList 추가, getList가 authorId=null로 findList/count 호출하도록 변경
src/main/java/com/example/board/service/CommentService.java        # getMyList 추가
src/main/java/com/example/board/mapper/BoardMapper.java            # findList/count에 authorId 파라미터 추가
src/main/resources/mapper/BoardMapper.xml                          # searchCondition에 authorId 조건 추가
src/main/java/com/example/board/repository/CommentRepository.java  # findByUserIdOrderByCreatedAtDesc(board fetch join) 추가
src/main/java/com/example/board/entity/Comment.java                # user_id 인덱스 추가
src/main/java/com/example/board/security/SecurityConfig.java       # PERMIT_ALL_PATHS에 "/mypage.html" 추가
src/main/resources/static/js/nav.js                                 # "마이페이지" 링크 추가
src/main/resources/static/js/index.js                               # "마이페이지" 링크 추가
src/main/resources/static/css/style.css                             # 마이페이지 프로필/탭 스타일 추가

src/test/java/com/example/board/service/BoardServiceTest.java      # getMyList 테스트 추가
src/test/java/com/example/board/service/CommentServiceTest.java    # getMyList 테스트 추가
src/test/java/com/example/board/mapper/BoardMapperTest.java        # authorId 필터 테스트 추가, findList/count 호출부 시그니처 반영
src/test/java/com/example/board/controller/UserControllerTest.java # BoardService/CommentService 의존성 반영, myBoards/myComments 테스트 추가
```

### 주요 변경사항
- 별도 `UserService`를 새로 만들지 않고, 기존 `BoardService`/`CommentService`에 `getMyList`를 추가해 `UserController`가 재사용하는 구조로 구현했다(계획대로).
- `BoardMapper.findList`/`count`에 `authorId` 파라미터를 추가해 게시글 목록 검색과 "내가 쓴 글" 조회가 같은 쿼리를 공유하도록 했다(쿼리 중복 없음).
- `CommentRepository.findByUserIdOrderByCreatedAtDesc`에 `@EntityGraph(attributePaths = "board")`를 적용해 댓글마다 게시글 제목을 보여줄 때 N+1이 발생하지 않도록 했다.
- `Comment` 테이블에 `user_id` 인덱스를 추가했다(`ddl-auto=update`로 자동 반영).
- 화면은 기존 `auth.js`/`nav.js`/`board/common.js`를 그대로 재사용했고, 별도 유틸 파일을 추가하지 않았다.

### 테스트 결과
- `./mvnw test` 최종 57건 전체 통과(BUILD SUCCESS) — 기존 52건 + 신규 5건(`BoardServiceTest.getMyList`, `CommentServiceTest.getMyList`, `BoardMapperTest.작성자_ID로_게시글_목록을_필터링한다`, `UserControllerTest` 2건).
- 로컬 앱을 별도 포트(8083)로 띄워 Playwright 헤드리스 브라우저로 회원가입 → 로그인 → 게시글 2개 작성(각각 댓글 작성) → 마이페이지 진입 → 내 정보 표시 확인 → "내가 쓴 글" 탭(2건) → "내가 쓴 댓글" 탭(2건, 게시글 제목 포함) → 댓글 클릭 시 해당 게시글 상세로 정상 이동 → 비로그인 상태로 `/mypage.html` 접근 시 로그인 화면으로 리다이렉트까지 전체 골든 패스를 확인했다. 콘솔 에러·실패한 요청 없음.
- 검증에 사용한 테스트 계정/게시글/댓글은 확인 후 로컬 DB에서 직접 정리했다(아래 "발생한 문제" 3번 참고 — 이전 세션에 정리하지 않아 문제가 됐던 것을 반면교사로 이번엔 바로 정리함).

### 발생한 문제 및 해결 방법
1. **`mvn test` 1회차에서 `BoardApplicationTests`/`BoardMapperTest`가 컨텍스트 로딩 자체에 실패** (`Unable to determine Dialect without JDBC metadata`). 로컬 Docker MySQL 컨테이너는 정상 기동 중이었고(`docker ps`, `mysqladmin ping`, 앱 계정으로 직접 접속 모두 성공), 재실행하니 바로 사라져 일시적인 연결 타이밍 문제로 판단했다(재현되지 않음, 코드 문제 아님).
2. **재실행 후 `BoardMapperTest.키워드와_카테고리로_게시글_목록을_검색하고_페이징한다`에서 `freeCount`가 3이 아니라 6으로 나옴**: 원인을 조사한 결과, 지난 세션에 `write.js` 리다이렉트 버그를 Playwright로 재현·검증하면서 만든 테스트용 게시글 3건(id 60~62, FREE 카테고리)이 로컬 MySQL에 정리되지 않고 남아있었다. 이 테스트가 `category=FREE` 전체 개수를 세는 방식이라 leftover 데이터까지 합산되어 실패한 것으로, 이번 마이페이지 기능과는 무관했다. DB 정리(`DELETE`)는 auto mode 분류기가 파괴적 작업으로 판단해 한 번 차단했고, 사용자에게 "leftover 데이터 삭제" vs "테스트를 키워드로 더 견고하게 수정" 중 선택을 요청해 전자로 진행 승인을 받은 뒤 정리했다.
3. **검증 과정에서 만든 새 테스트 데이터도 동일한 문제를 반복할 뻔함**: 2번 문제를 겪은 직후라, 이번에는 Playwright 검증으로 만든 게시글/댓글(id 78~80)을 검증 직후 바로 삭제해 동일 문제가 재발하지 않도록 했다.

### Plan과 실제 구현의 차이점
`docs/plans/003-my-page.md`의 "구현 결과와의 차이점" 절 참고 — 설계상 계획과의 차이는 없다. 검증 과정에서 이 기능과 무관한 기존 테스트의 데이터 오염 문제를 발견해 함께 정리했다.
