# 003. 마이페이지 (내가 쓴 글/댓글 조회)

## 작업 목적
`002-board-crud.md`로 게시글/댓글 CRUD(API+화면)까지 갖췄지만, 로그인한 사용자가 본인이 작성한 게시글/댓글을 모아서 확인할 방법이 없다. 이번 Plan에서 마이페이지(API+화면)를 구현해 이를 지원한다.

이번 범위는 **조회 전용**이다. 게시글/댓글 수정·삭제는 이미 `002-board-crud.md`에서 각 상세 화면에 구현되어 있으므로, 마이페이지에서는 목록만 보여주고 항목 클릭 시 기존 상세 화면으로 이동시킨다(마이페이지 안에서 인라인 수정/삭제는 만들지 않음 — 중복 구현 방지).

## 요구사항
- [x] 마이페이지 진입 시 내 정보(이메일/이름/권한) 표시
- [x] 내가 쓴 게시글 목록 (페이징, 제목/카테고리/작성일/조회수/댓글수 표시, 클릭 시 게시글 상세로 이동)
- [x] 내가 쓴 댓글 목록 (페이징, 댓글 내용/작성일 + 어느 게시글의 댓글인지 표시, 클릭 시 해당 게시글 상세로 이동)
- [x] 로그인 필요 (비로그인 접근 시 로그인 화면으로 이동)
- [x] 홈/게시판 화면 네비게이션에 "마이페이지" 링크 추가(로그인 상태일 때만)

### 확인된 사용자 결정 사항
- 좋아요/추천, 관리자(ADMIN) 기능은 이번 범위 밖 — 별도 Plan에서 다룬다.
- 마이페이지에서 게시글/댓글을 직접 수정·삭제하지 않는다(기존 상세 화면 재사용).

## 현재 구조 및 관련 코드
- `UserController`: `GET /api/users/me`만 존재. `UserService`는 없고, `userDetails.getUser()`를 컨트롤러에서 직접 사용 중.
- `BoardMapper.findList(keyword, category, offset, limit)` / `count(keyword, category)`: 작성자(`authorId`) 필터가 없다 — 내가 쓴 글만 걸러내려면 파라미터 추가가 필요하다.
- `CommentRepository`는 `findByBoardIdOrderByCreatedAtAsc(boardId)`만 있고, 사용자별 조회가 없다. `CommentResponse`는 "게시글 하나에 딸린 댓글" 전용 DTO라 게시글 제목/링크 정보가 없어 마이페이지에는 그대로 못 쓴다 — 어느 글의 댓글인지 알아야 하므로 새 응답 DTO가 필요하다.
- `Comment` 테이블은 `board_id`에만 인덱스가 있고 `user_id` 인덱스가 없다(`Comment.java` `@Table(indexes=...)`).
- 화면은 001/002와 동일한 패턴(정적 HTML + vanilla JS + fetch)을 따른다. `js/auth.js`(`authFetch`, `isLoggedIn`), `js/nav.js`(공용 네비게이션), `js/board/common.js`(`escapeHtml`, `formatDateTime`, `categoryLabel`, `renderPagination`)를 그대로 재사용할 수 있다.
- `SecurityConfig.PERMIT_ALL_PATHS`에 없는 `/api/**` 경로(`/api/users/**` 포함)는 기본적으로 `anyRequest().authenticated()`로 인증이 필요하다 — 신규 API를 위한 별도 화이트리스트 조정은 필요 없다. 다만 마이페이지 화면 자체(`/mypage.html`)는 다른 화면들과 마찬가지로 `PERMIT_ALL_PATHS`에 추가해야 정적 리소스 접근이 막히지 않는다(로그인 여부 판단은 화면 진입 후 JS가 처리 — `board/write.html`과 동일 패턴).

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/dto/response/MyCommentResponse.java   # id, content, boardId, boardTitle, createdAt, updatedAt

src/main/resources/static/mypage.html
src/main/resources/static/js/mypage.js
```

### 수정
```
src/main/java/com/example/board/controller/UserController.java     # GET /api/users/me/boards, GET /api/users/me/comments 추가
src/main/java/com/example/board/service/BoardService.java          # getMyList(userId, page, size) 추가, 기존 getList 관련 마퍼 호출에 authorId=null 전달하도록 변경
src/main/java/com/example/board/service/CommentService.java        # getMyList(userId, page, size) 추가
src/main/java/com/example/board/mapper/BoardMapper.java            # findList/count에 authorId 파라미터 추가
src/main/resources/mapper/BoardMapper.xml                          # searchCondition에 authorId 조건 추가
src/main/java/com/example/board/repository/CommentRepository.java  # Page<Comment> findByUserIdOrderByCreatedAtDesc(userId, pageable) 추가 (board fetch join)
src/main/java/com/example/board/entity/Comment.java                # user_id 인덱스 추가
src/main/java/com/example/board/security/SecurityConfig.java       # PERMIT_ALL_PATHS에 "/mypage.html" 추가
src/main/resources/static/js/nav.js                                 # 로그인 상태일 때 "마이페이지" 링크 추가
src/main/resources/static/js/index.js                               # 로그인 상태일 때 nav-links에 "마이페이지" 링크 추가
src/main/resources/static/css/style.css                             # 마이페이지 탭/목록 스타일 추가

src/test/java/com/example/board/service/BoardServiceTest.java      # getMyList 케이스 추가, getList 시그니처 변경 반영
src/test/java/com/example/board/service/CommentServiceTest.java    # getMyList 케이스 추가
src/test/java/com/example/board/mapper/BoardMapperTest.java        # authorId 필터 케이스 추가
```

## 구현 방법

### API

#### 1. `BoardMapper`에 작성자 필터 추가
- `findList`/`count`에 `@Param("authorId") Long authorId`를 추가하고, `BoardMapper.xml`의 `searchCondition` 공통 `<sql>`에 `<if test="authorId != null">AND b.user_id = #{authorId}</if>`를 추가한다. 목록 검색(키워드/카테고리)과 "내가 쓴 글" 조회가 같은 쿼리를 공유하게 되어 중복 쿼리를 만들지 않는다(CLAUDE.md 규칙 준수).
- 기존 `BoardService.getList(keyword, category, page, size)`는 `authorId`에 `null`을 넘기도록 호출부만 수정한다(동작 변화 없음).

#### 2. `BoardService.getMyList`
- `getMyList(Long userId, int page, int size)` 추가: `boardMapper.findList(null, null, userId, offset, size)` / `boardMapper.count(null, null, userId)`로 조회해 기존과 동일한 `PageResponse<BoardListItemResponse>`를 반환한다(키워드/카테고리 필터는 마이페이지 1차 범위에서는 제공하지 않음).

#### 3. `CommentRepository` / `CommentService.getMyList` / `MyCommentResponse`
- `CommentRepository`에 `@EntityGraph(attributePaths = "board") Page<Comment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable)` 추가(N+1 방지 — 댓글마다 게시글 제목을 보여줘야 하므로 board를 fetch join).
- `MyCommentResponse.from(Comment)`로 변환.
- `CommentService.getMyList(Long userId, int page, int size)`: `PageRequest.of(page, size)`로 조회 후 `PageResponse<MyCommentResponse>`로 감싼다.

#### 4. `UserController`
- `BoardService`, `CommentService`를 주입받아(`@RequiredArgsConstructor`) 아래 두 엔드포인트를 추가한다. Controller가 Repository/Mapper를 직접 호출하지 않고 기존 Service를 통해서만 접근한다(CLAUDE.md 아키텍처 규칙 준수).
```java
@GetMapping("/me/boards")
public ResponseEntity<PageResponse<BoardListItemResponse>> myBoards(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) { ... }

@GetMapping("/me/comments")
public ResponseEntity<PageResponse<MyCommentResponse>> myComments(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) { ... }
```
- 별도 `UserService`는 만들지 않는다 — 게시글/댓글 데이터 접근 로직은 이미 `BoardService`/`CommentService`에 있으므로, 새 서비스 계층을 추가하기보다 기존 서비스를 재사용하는 쪽이 CLAUDE.md의 "과도한 책임 분산 금지"·중복 최소화 원칙에 맞는다.

### 화면

#### 5. `mypage.html` + `mypage.js`
- 진입 시 `isLoggedIn()` 아니면 `/auth/login.html`로 이동(다른 인증 필요 화면과 동일 패턴).
- `/api/users/me`로 내 정보(이메일/이름/권한) 로드해 상단에 표시.
- 탭 2개: "내가 쓴 글" / "내가 쓴 댓글". 각 탭 활성화 시 해당 API를 `authFetch`로 호출하고 `board/common.js`의 `renderPagination`으로 페이지네이션 렌더링.
- 게시글 목록 행: `board/list.html`과 유사한 형태(제목/카테고리/작성일/조회수/댓글수), 클릭 시 `/board/detail.html?id={id}`.
- 댓글 목록 행: 댓글 내용 일부 + "게시글: {boardTitle}" 형태로 표시, 클릭 시 `/board/detail.html?id={boardId}`.

#### 6. 네비게이션 / SecurityConfig
- `nav.js`, `index.js`의 로그인 상태 분기에 `<a href="/mypage.html">마이페이지</a>` 추가.
- `SecurityConfig.PERMIT_ALL_PATHS`에 `"/mypage.html"` 추가.

## 데이터베이스 변경 사항
스키마 신규 생성 없음(기존 `boards`, `comments` 테이블 재사용). `comments` 테이블에 `user_id` 인덱스를 추가한다(사용자별 댓글 조회 성능, `ddl-auto=update`로 자동 반영).

| 테이블 | 변경 |
|---|---|
| comments | 인덱스 추가: `idx_comments_user_id (user_id)` |

## API 변경 사항

| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| GET | /api/users/me/boards?page=&size= | 내가 쓴 게시글 목록 | O |
| GET | /api/users/me/comments?page=&size= | 내가 쓴 댓글 목록(게시글 제목 포함) | O |

기존 엔드포인트 변경/제거 없음.

## 테스트 계획
- **Service 단위 테스트** (Mockito)
  - `BoardServiceTest`: `getMyList` 성공 케이스 추가. 기존 `getList` 테스트는 `boardMapper.findList`/`count` 호출 시그니처에 `authorId` 인자(= null)가 추가되는 점만 반영.
  - `CommentServiceTest`: `getMyList` 성공 케이스(페이징, board 정보 포함 매핑) 추가.
- **Mapper 통합 테스트**
  - `BoardMapperTest`에 `authorId` 필터 케이스 추가(로컬 Docker MySQL 대상, 기존 방식 재사용).
- **Controller 테스트**
  - `UserController`에 대한 테스트 추가. `001-auth.md`에서 확인된 제약(슬라이스 테스트에서 `SecurityConfig` 전체 로드 어려움)과 동일한 사유로, 필요 시 컨트롤러 메서드 직접 호출 방식을 적용한다(`AuthControllerTest`의 로그아웃 테스트 패턴 참고).
- **화면**: 자동화 테스트 대상 코드가 아니므로(기존 관례 동일) 브라우저로 다음 골든 패스를 확인한다.
  1. 비로그인 상태로 `/mypage.html` 접근 시 로그인 화면으로 이동
  2. 로그인 → 마이페이지 진입 → 내 정보 표시 확인
  3. "내가 쓴 글" 탭: 목록/페이징 확인, 항목 클릭 시 상세로 이동
  4. "내가 쓴 댓글" 탭: 목록/페이징/게시글 제목 표시 확인, 항목 클릭 시 해당 게시글 상세로 이동
- 전체 구현 후 `./mvnw test`로 기존 테스트 포함 전체 회귀 확인.

## 작업 상태
- Completed

### 구현 결과와의 차이점
- 게시글 목록의 "댓글 내용" 응답 필드는 계획대로 `MyCommentResponse`로 신설했고, 나머지(엔드포인트, 서비스/리포지토리 구조, 화면 구성)도 계획대로 구현했다. 설계상 큰 차이는 없다.
- 구현 자체와는 무관하게, 검증 과정에서 기존 `BoardMapperTest`가 실패하는 것을 발견했다 — 원인과 조치는 Work Log 참고(요약: 이전 세션에 브라우저로 수동 검증하며 만든 테스트용 게시글이 로컬 DB에 남아있어 카운트 기반 검증이 어긋났고, 남은 데이터를 정리해 해결했다. 이번 기능의 버그는 아니었다).
