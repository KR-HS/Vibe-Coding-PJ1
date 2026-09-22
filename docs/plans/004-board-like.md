# 004. 게시글 좋아요 (API + 화면)

## 작업 목적
로그인한 사용자가 게시글에 좋아요를 누르고(취소도 가능) 목록/상세에서 좋아요 수를 확인할 수 있는 기능을 API+화면으로 함께 구현한다.

## 요구사항
- [x] 게시글 좋아요 추가/취소 (로그인 필요)
- [x] 한 사용자는 게시글 하나에 좋아요를 최대 1개만 누를 수 있다(중복 좋아요 방지)
- [x] 게시글 목록에 좋아요 수 표시
- [x] 게시글 상세에 좋아요 수 + 내가 눌렀는지 여부 표시, 버튼으로 토글
- [x] 비로그인 사용자도 좋아요 수는 볼 수 있지만, 누르면 로그인 화면으로 유도
- [x] 마이페이지에서 "내가 좋아요 누른 글" 목록을 확인할 수 있다 (사용자 요청으로 이 Plan에 추가)

### 확인된 사용자 결정 사항
- 이번 범위는 **게시글 좋아요만** 다룬다. 댓글 좋아요는 범위 밖(필요 시 별도 Plan).
- 좋아요 추가(POST)/취소(DELETE) API는 **멱등(idempotent)** 하게 설계한다. 이미 좋아요 누른 상태에서 다시 POST 하거나, 안 누른 상태에서 DELETE 해도 에러 없이 현재 상태(`liked`, `likeCount`)를 그대로 반환한다. 더블클릭 등으로 인한 예외 처리를 프론트에서 따로 신경 쓰지 않아도 되게 하기 위함이다. 대신 DB에는 `(board_id, user_id)` 유니크 제약을 걸어 동시 요청으로 인한 중복 삽입을 원천 차단한다.
- 랭킹/인기글 정렬, 좋아요 알림 등은 범위 밖.

## 현재 구조 및 관련 코드
- `Board` 엔티티에는 `viewCount`만 있고 좋아요 관련 필드/테이블이 없다.
- `BoardMapper.findList`/`count`는 `users` JOIN + 댓글 수 서브쿼리로 `BoardListItemResponse`를 만든다(`002-board-crud.md` 참고) — 좋아요 수도 같은 방식(서브�쿼리)으로 추가할 수 있다.
- `BoardController.getDetail(id)`는 현재 인증 여부와 무관하게 동작하며 `@AuthenticationPrincipal`을 받지 않는다. "내가 좋아요 눌렀는지"를 상세 응답에 포함하려면 로그인 여부를 알아야 하는데, 이 엔드포인트는 `GET /api/boards/**`가 `permitAll`이라 비로그인 접근이 정상이다. Spring Security에서 `@AuthenticationPrincipal CustomUserDetails userDetails`는 인증되지 않은 요청에서는 타입이 맞지 않아 자동으로 `null`이 주입된다(`errorOnInvalidType` 기본값 `false`) — 이 특성을 이용해 permitAll을 유지한 채로 로그인 여부를 옵셔널하게 받을 수 있다.
- 좋아요 추가/취소 API(`POST`/`DELETE /api/boards/{boardId}/likes`)는 `SecurityConfig`의 `PERMIT_ALL_PATHS`나 `GET /api/boards/**` 화이트리스트 어디에도 해당하지 않으므로, 추가 설정 없이 기존 `anyRequest().authenticated()`에 의해 자동으로 인증이 필요해진다(SecurityConfig 변경 불필요).
- 화면 패턴(정적 HTML + vanilla JS + fetch, `auth.js`/`board/common.js` 재사용)은 001/002/003과 동일.

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/entity/BoardLike.java
src/main/java/com/example/board/repository/BoardLikeRepository.java
src/main/java/com/example/board/dto/response/LikeResponse.java   # likeCount, liked
```

### 수정
```
src/main/java/com/example/board/controller/BoardController.java   # POST/DELETE /api/boards/{boardId}/likes 추가, getDetail이 로그인 사용자(nullable) 인지하도록 변경
src/main/java/com/example/board/service/BoardService.java         # like/unlike, getDetail(boardId, userId)로 시그니처 변경
src/main/java/com/example/board/dto/response/BoardDetailResponse.java   # likeCount, liked 필드 추가
src/main/java/com/example/board/dto/response/BoardListItemResponse.java # likeCount 필드 추가
src/main/java/com/example/board/mapper/BoardMapper.java            # 반환 컬럼에 likeCount 추가(파라미터 변경 없음)
src/main/resources/mapper/BoardMapper.xml                          # likeCount 서브쿼리 추가

src/main/resources/static/board/list.html                          # 목록 테이블에 "좋아요" 컬럼 추가
src/main/resources/static/js/board/list.js                         # 좋아요 수 렌더링
src/main/resources/static/board/detail.html                        # 좋아요 버튼/카운트 UI 추가
src/main/resources/static/js/board/detail.js                       # 좋아요 토글 로직
src/main/resources/static/css/style.css                            # 좋아요 버튼 스타일

src/main/java/com/example/board/controller/UserController.java    # GET /api/users/me/likes 추가
src/main/resources/static/mypage.html                              # "좋아요한 글" 탭 추가
src/main/resources/static/js/mypage.js                              # 좋아요한 글 탭 로직

src/test/java/com/example/board/service/BoardServiceTest.java
src/test/java/com/example/board/controller/BoardControllerTest.java
src/test/java/com/example/board/controller/UserControllerTest.java
src/test/java/com/example/board/mapper/BoardMapperTest.java
```

## 구현 방법

### API

#### 1. `BoardLike` 엔티티 + `BoardLikeRepository`
- `BoardLike`: `id`, `board`(다대일 FK), `user`(다대일 FK), `createdAt`. 다른 엔티티와 동일하게 `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@PrePersist`로 시각 관리.
- `@Table(name = "board_likes", uniqueConstraints = @UniqueConstraint(name = "uk_board_likes_board_user", columnNames = {"board_id", "user_id"}))` — 동시 요청으로 인한 중복 좋아요를 DB 레벨에서 방지(유니크 제약이 board_id 선두 컬럼이라 게시글별 좋아요 수 집계에도 활용 가능, 별도 인덱스 추가하지 않음).
- `BoardLikeRepository`: `boolean existsByBoardIdAndUserId(Long boardId, Long userId)`, `void deleteByBoardIdAndUserId(Long boardId, Long userId)`, `long countByBoardId(Long boardId)`.

#### 2. `BoardService.like` / `unlike` (멱등)
```java
@Transactional
public LikeResponse like(Long userId, Long boardId) {
    Board board = getBoardOrThrow(boardId);
    if (!boardLikeRepository.existsByBoardIdAndUserId(boardId, userId)) {
        boardLikeRepository.save(BoardLike.builder()
                .board(board)
                .user(userRepository.getReferenceById(userId))
                .build());
    }
    return new LikeResponse(boardLikeRepository.countByBoardId(boardId), true);
}

@Transactional
public LikeResponse unlike(Long userId, Long boardId) {
    getBoardOrThrow(boardId); // 존재하지 않는 게시글이면 404
    boardLikeRepository.deleteByBoardIdAndUserId(boardId, userId);
    return new LikeResponse(boardLikeRepository.countByBoardId(boardId), false);
}
```
- 동시 중복 요청으로 유니크 제약 위반이 나면(`DataIntegrityViolationException`) `like()`가 이를 잡아 "이미 좋아요된 것"으로 간주하고 정상 응답을 반환한다(멱등성 보장, 경합 상황 대비).

#### 3. `BoardService.getDetail(boardId, userId)`
- 기존 `getDetail(Long boardId)`에 `Long userId`(nullable) 파라미터를 추가한다. `userId`가 있으면 `boardLikeRepository.existsByBoardIdAndUserId`로 `liked` 여부를 계산하고, 없으면 `liked=false`로 고정한다. `likeCount`는 로그인 여부와 무관하게 항상 계산한다.
- `BoardDetailResponse`에 `likeCount`, `liked` 필드를 추가한다.

#### 4. `BoardController`
```java
@GetMapping("/{id}")
public ResponseEntity<BoardDetailResponse> getDetail(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long id) {
    Long userId = userDetails != null ? userDetails.getId() : null;
    return ResponseEntity.ok(boardService.getDetail(id, userId));
}

@PostMapping("/{id}/likes")
public ResponseEntity<LikeResponse> like(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long id) {
    return ResponseEntity.ok(boardService.like(userDetails.getId(), id));
}

@DeleteMapping("/{id}/likes")
public ResponseEntity<LikeResponse> unlike(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long id) {
    return ResponseEntity.ok(boardService.unlike(userDetails.getId(), id));
}
```

#### 5. 목록 좋아요 수 (`BoardMapper`)
- `BoardListItemResponse`에 `likeCount` 필드를 추가하고, `BoardMapper.xml`의 `findList` SELECT 절에 `(SELECT COUNT(*) FROM board_likes bl WHERE bl.board_id = b.id) AS likeCount`를 댓글 수 서브쿼리와 같은 방식으로 추가한다(파라미터/메서드 시그니처 변경 없음, 반환 컬럼만 추가).

#### 8. 내가 좋아요 누른 글 목록 (`002/003`에서 확립한 패턴 재사용)
- `BoardMapper.findList`/`count`에 `likedByUserId` 파라미터를 추가한다(`authorId`와 같은 방식으로 `searchCondition`에 조건 추가). 게시글별 좋아요 여부는 `EXISTS (SELECT 1 FROM board_likes bl2 WHERE bl2.board_id = b.id AND bl2.user_id = #{likedByUserId})`로 필터링한다(이미 SELECT 절에 있는 `likeCount` 서브쿼리와 별개 서브쿼리이므로 별칭을 `bl2`로 구분).
- `BoardService.getLikedList(Long userId, int page, int size)` 추가 — `getMyList`와 동일한 형태로 `boardMapper.findList(null, null, null, userId, offset, size)` 호출.
- `UserController.myLikes`: `GET /api/users/me/likes?page=&size=` 추가, `boardService.getLikedList(userDetails.getId(), page, size)` 호출(인증 필요, `/api/users/**`는 기본적으로 인증 필요이므로 SecurityConfig 변경 불필요).
- `mypage.html`/`mypage.js`에 "내가 쓴 글" / "내가 쓴 댓글"과 같은 방식으로 "좋아요한 글" 탭을 추가한다(목록 UI는 `list.js`의 게시글 행 렌더링과 동일한 형태 재사용).

### 화면

#### 6. 게시글 목록
- `board/list.html` 테이블 헤더에 "좋아요" 컬럼 추가, `list.js`에서 `board.likeCount` 렌더링(조회수 컬럼과 동일한 형태).

#### 7. 게시글 상세
- `detail.html`에 좋아요 버튼(예: "♥ 좋아요 12") 추가. 로그인 상태면 클릭 시 `liked` 여부에 따라 `authFetch(POST)`/`authFetch(DELETE)`를 호출하고 응답의 `likeCount`/`liked`로 버튼 상태·숫자를 즉시 갱신한다(재조회 불필요). 비로그인 상태면 클릭 시 로그인 페이지로 이동(`write.html` 진입 가드와 같은 패턴을 버튼 클릭 시점에 적용).
- 버튼 스타일: `liked=true`일 때 강조색(예: 배경 채움), `false`일 때 아웃라인.

## 데이터베이스 변경 사항
`board_likes` 테이블 신규 생성.

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| board_id | BIGINT | NOT NULL, FK → boards.id |
| user_id | BIGINT | NOT NULL, FK → users.id |
| created_at | DATETIME | NOT NULL |

- 유니크 제약: `(board_id, user_id)` — 중복 좋아요 방지 + 게시글별 집계 조회에도 사용.
- 게시글 삭제 시 좋아요 레코드도 Service 계층에서 명시적으로 삭제한다(`BoardService.delete`에 `boardLikeRepository.deleteAllByBoardId` 또는 목록 조회 후 삭제 추가 — 기존 댓글/첨부파일 삭제와 동일한 패턴, `ON DELETE CASCADE` 미사용 원칙 유지).

## API 변경 사항

| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| POST | /api/boards/{id}/likes | 좋아요 추가(멱등) | O |
| DELETE | /api/boards/{id}/likes | 좋아요 취소(멱등) | O |
| GET | /api/users/me/likes?page=&size= | 내가 좋아요 누른 글 목록 | O |

기존 `GET /api/boards`, `GET /api/boards/{id}` 응답에 `likeCount`(목록/상세) / `liked`(상세) 필드가 추가된다(기존 필드는 변경 없음, 하위 호환).

## 테스트 계획
- **Service 단위 테스트**: `like` 성공(신규 좋아요), `like` 멱등(이미 좋아요한 상태에서 다시 호출해도 카운트 안 늘어남), `unlike` 성공, `unlike` 멱등(안 눌렀는데 호출해도 에러 없음), `getDetail`이 로그인 사용자의 `liked` 여부를 올바르게 반영하는지, 비로그인(`userId=null`)일 때 `liked=false` 고정.
- **Mapper 통합 테스트**: `BoardMapperTest`에 좋아요 있는/없는 게시글이 섞인 목록에서 `likeCount`가 올바르게 집계되는지 케이스 추가(로컬 Docker MySQL 대상).
- **Controller 테스트**: `BoardControllerTest`(`@WebMvcTest`)에 좋아요 추가/취소 API 상태 코드·응답 바디 검증 추가.
- **화면**: 브라우저 골든 패스 — 로그인 후 상세에서 좋아요 클릭 → 카운트/버튼 상태 변경 확인 → 새로고침해도 유지되는지 → 다시 클릭해 취소 → 목록에서도 좋아요 수 반영 확인 → 비로그인 상태로 좋아요 버튼 클릭 시 로그인 화면 이동 확인.
- **`getLikedList`**: 좋아요한 글이 목록에 정확히 나오는지(`BoardMapperTest`에 `likedByUserId` 필터 케이스 추가), 좋아요 취소 후에는 목록에서 빠지는지.
- **화면**: 마이페이지 "좋아요한 글" 탭에서 목록/페이징 확인, 항목 클릭 시 상세로 이동.
- 전체 구현 후 `./mvnw test`로 기존 테스트 포함 전체 회귀 확인.

## 작업 상태
- Completed

### 구현 결과와의 차이점
- 설계(엔티티, 멱등 API, `liked`/`likeCount` 필드 추가, 화면 구성)는 계획대로 구현했다.
- `board/detail.html`의 게시글 상세 조회를 `fetch`에서 `authFetch`로 변경했다(계획에는 명시하지 않았던 세부사항). 로그인 사용자의 `liked` 여부를 정확히 받으려면 Authorization 헤더가 필요했기 때문이다. `authFetch`는 토큰이 없어도 `JwtAuthenticationFilter`가 안전하게 무시하므로 비로그인 사용자에게도 문제없이 동작한다(Work Log 참고).
- Controller 테스트는 계획에서 예상한 대로 `@WebMvcTest` 슬라이스의 알려진 제약(`SecurityConfig` 미로딩으로 `@AuthenticationPrincipal`이 MockMvc 실제 디스패치에서 정상 해석되지 않음) 때문에, 상세 조회 테스트도 기존 수정/삭제 테스트와 동일하게 컨트롤러 직접 호출 방식으로 작성했다.
- 구현과 무관하게, 검증 중 기존 `BoardMapperTest`가 다시 한번(이번엔 사용자가 직접 만든 게시글 때문에) 실패하는 것을 발견해, 데이터를 지우는 대신 테스트 자체를 특정 키워드/작성자 ID로 좁혀 공유 DB 상태에 영향받지 않도록 고쳤다(Work Log 참고).
- "내가 좋아요 누른 글" 목록은 사용자가 마이페이지에 없다는 점을 지적해 이 Plan에 추가로 포함시켰다. `BoardMapper.findList`/`count`에 `likedByUserId` 파라미터를 추가하는 방식으로 기존 쿼리를 재사용했고, `UserController.myLikes`, 마이페이지 "좋아요한 글" 탭을 계획대로 구현했다.
