# 004. 게시글 좋아요 (API + 화면) - Work Log

## 2026-09-22

### 작업 내용
`docs/plans/004-board-like.md`에 따라 게시글 좋아요 기능(API+화면)을 구현했다. 추가/취소 API는 멱등하게 설계했고, 목록/상세 화면에 좋아요 수와 토글 버튼을 추가했다.

### 변경된 파일

**신규**
```
src/main/java/com/example/board/entity/BoardLike.java
src/main/java/com/example/board/repository/BoardLikeRepository.java
src/main/java/com/example/board/dto/response/LikeResponse.java
```

**수정**
```
src/main/java/com/example/board/controller/BoardController.java   # POST/DELETE /api/boards/{id}/likes 추가, getDetail이 로그인 사용자(nullable) 인지
src/main/java/com/example/board/service/BoardService.java         # like/unlike(멱등), getDetail(boardId, userId), delete 시 좋아요도 함께 삭제
src/main/java/com/example/board/dto/response/BoardDetailResponse.java   # likeCount, liked 필드 추가
src/main/java/com/example/board/dto/response/BoardListItemResponse.java # likeCount 필드 추가
src/main/java/com/example/board/mapper/BoardMapper.java, BoardMapper.xml # likeCount 서브쿼리 추가
src/main/resources/static/board/list.html, js/board/list.js        # 목록에 "좋아요" 컬럼 추가
src/main/resources/static/board/detail.html, js/board/detail.js    # 좋아요 토글 버튼 추가, 상세 조회를 authFetch로 변경
src/main/resources/static/css/style.css                            # 좋아요 버튼/컬럼 스타일 추가

src/test/java/com/example/board/service/BoardServiceTest.java
src/test/java/com/example/board/controller/BoardControllerTest.java
src/test/java/com/example/board/mapper/BoardMapperTest.java
src/test/java/com/example/board/controller/UserControllerTest.java # BoardListItemResponse 생성자에 likeCount 필드 반영
```

### 주요 변경사항
- `board_likes` 테이블에 `(board_id, user_id)` 유니크 제약을 걸어 동시 요청으로 인한 중복 좋아요를 DB 레벨에서 방지했다.
- `like`/`unlike`를 멱등하게 구현했다: 이미 좋아요한 상태에서 다시 좋아요를 눌러도 중복 저장하지 않고, 좋아요하지 않은 상태에서 취소해도 에러 없이 현재 상태를 반환한다.
- 상세 조회(`GET /api/boards/{id}`)는 비로그인도 가능해야 해서 `@AuthenticationPrincipal CustomUserDetails userDetails`를 nullable로 받는다. Spring Security는 인증되지 않은 요청에서 타입이 맞지 않으면 자동으로 `null`을 주입하는 특성을 이용했다(계획대로).
- 좋아요 수는 기존 댓글 수와 동일한 서브쿼리 방식으로 `BoardMapper.xml`에 추가해 목록 조회 쿼리를 그대로 재사용했다.

### 테스트 결과
- `./mvnw test` 최종 66건 전체 통과(BUILD SUCCESS) — 기존 57건 + 신규 9건.
- 로컬 앱을 별도 포트(8084)로 띄워 Playwright 헤드리스 브라우저로 두 사용자(작성자/좋아요 누른 사용자)를 만들어 검증: 글 작성 → 상세에서 좋아요 초기 상태(0, 안 누름) 확인 → 다른 사용자가 좋아요 클릭 → 카운트 1로 증가 + 버튼 강조 → 새로고침해도 유지 → 다시 클릭해 취소 → 카운트 0으로 복귀 → 목록 화면에 좋아요 수 반영 확인 → 비로그인 상태로 좋아요 버튼 클릭 시 로그인 화면으로 이동까지 전체 골든 패스를 확인했다. 콘솔 에러 없음.
- 검증에 쓴 테스트 계정/게시글(id 116)은 확인 직후 정리했다. 이 DB에 사용자가 직접 만든 게시글(id 88, "." 제목)이 하나 남아있었는데, 이번에는 내 것이 아니므로 **건드리지 않았다**(아래 "발생한 문제" 참고).

### 발생한 문제 및 해결 방법
1. **`BoardControllerTest`의 상세 조회 MockMvc 테스트가 500으로 실패**: `getDetail`에 `@AuthenticationPrincipal CustomUserDetails userDetails`를 추가한 뒤 실제 MockMvc HTTP 디스패치로 테스트하니 `NullPointerException: Cannot invoke "User.getId()" because "this.user" is null`이 발생했다. 원인은 `@WebMvcTest(controllers = BoardController.class, ...)` 슬라이스가 `SecurityConfig`(`@EnableWebSecurity`)를 로드하지 않아 Spring Security의 `AuthenticationPrincipalArgumentResolver`가 등록되지 않고, Spring MVC가 `@AuthenticationPrincipal` 어노테이션을 무시한 채 `CustomUserDetails`를 일반 커맨드 객체로 취급해 Objenesis로 생성자를 우회한 빈 인스턴스(`user=null`)를 주입했기 때문이다(디버그 로그로 `SecurityContextHolder`의 인증은 실제로 `null`인데 `userDetails`는 non-null이라는 모순을 확인해 원인을 특정했다). 실제 운영 앱(SecurityConfig가 온전히 로드됨)에서는 발생하지 않는, 순수 테스트 슬라이스 한정 문제다. `001-auth.md`에서 이미 확인된 동일 제약(슬라이스 테스트가 SecurityConfig를 온전히 로드 못 함)에 대한 기존 해법대로, 이 테스트를 MockMvc 대신 컨트롤러 직접 호출 방식으로 바꿔 해결했다.
2. **`BoardMapperTest`가 다시 데이터 오염으로 실패**: `002/003` 작업 때와 같은 유형의 문제가 또 발생했다(`freeCount`가 3이 아니라 4). 이번엔 원인이 내 테스트 데이터가 아니라, **사용자가 직접 앱을 써보면서 만든 게시글**(id 88)이었다. 이전처럼 데이터를 지우는 대신, 이번엔 테스트 자체를 견고하게 고쳤다: 카테고리 전체 개수를 세던 부분을 이 테스트가 직접 만든 데이터만 걸리도록 고유 키워드("자유게시판")나 작성자 ID로 좁혔다. 이렇게 하면 공유 로컬 DB에 다른 데이터(수동 테스트든 다른 테스트든)가 얼마나 쌓여도 이 테스트는 영향받지 않는다 — 근본적인 해결.

### Plan과 실제 구현의 차이점
`docs/plans/004-board-like.md`의 "구현 결과와의 차이점" 절 참고 — `detail.js`의 상세 조회를 `authFetch`로 바꾼 점, 상세 조회 테스트를 컨트롤러 직접 호출로 작성한 점, `BoardMapperTest`를 견고하게 고친 점이 차이점이다. API/화면 설계 자체는 계획대로다.

## 2026-09-22 (추가) — 마이페이지 "내가 좋아요 누른 글" 탭 추가

### 작업 내용
사용자가 마이페이지에서 좋아요 누른 글을 확인할 수 없다는 걸 지적해, 같은 Plan(`004-board-like.md`)에 요구사항을 추가하고 구현했다.

### 변경된 파일
```
src/main/java/com/example/board/mapper/BoardMapper.java, BoardMapper.xml   # likedByUserId 파라미터 추가
src/main/java/com/example/board/service/BoardService.java                  # getLikedList 추가, 기존 findList/count 호출부에 likedByUserId=null 반영
src/main/java/com/example/board/controller/UserController.java            # GET /api/users/me/likes 추가
src/main/resources/static/mypage.html, js/mypage.js                        # "좋아요한 글" 탭 추가

src/test/java/com/example/board/mapper/BoardMapperTest.java                # likedByUserId 필터 테스트 추가, 기존 findList/count 호출부 시그니처 반영
src/test/java/com/example/board/service/BoardServiceTest.java              # getLikedList 테스트 추가
src/test/java/com/example/board/controller/UserControllerTest.java         # myLikes 테스트 추가
```

### 주요 변경사항
- `BoardMapper.findList`/`count`에 `likedByUserId` 파라미터를 추가했다. `authorId`와 같은 방식으로 `searchCondition`에 `EXISTS (SELECT 1 FROM board_likes bl2 WHERE bl2.board_id = b.id AND bl2.user_id = #{likedByUserId})` 조건을 추가해, 이미 있는 목록 조회 쿼리를 그대로 재사용했다(새 쿼리를 만들지 않음).
- 마이페이지에 "내가 쓴 글" / "내가 쓴 댓글"과 동일한 패턴으로 "좋아요한 글" 탭을 추가했고, 목록에는 작성자/좋아요 수까지 함께 표시했다.

### 테스트 결과
- `./mvnw test` 최종 69건 전체 통과 — 기존 66건 + 신규 3건(`BoardMapperTest`, `BoardServiceTest`, `UserControllerTest` 각 1건).
- 로컬 앱을 별도 포트(8086)로 띄워 Playwright로 게시글 2개 작성 → 1개만 좋아요 → 마이페이지 "좋아요한 글" 탭에 좋아요 누른 글만(1건) 정확히 표시되는지 → 항목 클릭 시 해당 게시글 상세로 이동하는지까지 확인했다. 콘솔 에러 없음.
- 검증에 쓴 테스트 게시글(id 138, 139)은 확인 직후 정리했다. 사용자가 직접 만든 게시글(id 88)은 이번에도 건드리지 않았다.

