# 006. 게시글 목록 캐싱 — JMeter 부하테스트 가이드

`docs/plans/006-board-list-cache.md`로 구현한 `GET /api/boards` 캐싱 효과를 JMeter로 실측하는 방법과, 실제로 돌려본 결과를 기록한다.

## 사전 준비
- 로컬 MySQL/Redis가 떠 있어야 한다(`docker compose up -d`).
- 앱을 직접 실행해서 띄워야 한다(JMeter는 애플리케이션 컨텍스트를 내부에서 띄우지 않고, 실제로 뜬 서버에 HTTP 요청을 보내는 블랙박스 도구다):
  ```
  mvn spring-boot:run
  ```
  기본 포트는 `8081`(application.properties의 `server.port`).

## 실행 방법
```
mvn clean verify -Pperf-test
```
- `-Pperf-test` 프로파일을 명시적으로 켜야만 동작한다. 평소 `mvn test`/`mvn install`에는 전혀 영향 없음.
- `clean`을 꼭 붙일 것 — 이전 실행의 HTML 리포트가 `target/jmeter/reports/board-list-load-test`에 남아있으면 "폴더가 비어있지 않다"는 에러로 실패한다.
- 결과: `target/jmeter/reports/board-list-load-test/index.html`(그래프 포함 리포트), `target/jmeter/results/board-list-load-test.csv`(원본 데이터). `target/`는 `clean`할 때마다 사라지므로, 비교해서 보고 싶은 리포트는 실행 직후 다른 폴더로 복사해둔다.

## 시나리오 파라미터 바꾸기
기본값은 동시 사용자 20명 × 반복 50회, ramp-up 5초, 대상 `localhost:8081`이다. 필요하면 `-D`로 덮어쓴다:
```
mvn clean verify -Pperf-test -Djmeter.threads=50 -Djmeter.loops=100 -Djmeter.rampup=10 -Djmeter.port=8081
```

## 캐시 켬/끔 비교 절차
1. `src/main/resources/application.properties`(또는 `application-local.properties`)에서 `cache.board-list.enabled=false`로 설정하고 앱 재시작.
2. `mvn clean verify -Pperf-test` 실행 → 리포트를 `perf-results/no-cache/`처럼 다른 폴더로 복사해서 보관.
3. `cache.board-list.enabled=true`로 바꾸고 앱 재시작.
4. **1번과 동일한 파라미터**로 `mvn clean verify -Pperf-test` 다시 실행 → 리포트를 `perf-results/cached/`로 복사.
5. 두 리포트의 `Aggregate Report`(평균/최소/최대 응답시간, TPS)를 비교한다.

## 실제로 돌려본 결과 (2026-09-28, 게시글 1건 기준)

| | 요청 수 | 평균 응답시간 | 처리량 | 에러율 |
|---|---|---|---|---|
| 캐시 끔 | 200 (10 threads × 20 loops) | **24ms** | 84.3/s | 0% |
| 캐시 켬 | 200 (10 threads × 20 loops) | **28ms** | 89.4/s | 0% |

**캐시를 켰는데 오히려 평균 응답시간이 더 길게 나왔다.** 이유는 이렇다:
- 이 시점 DB의 `boards` 테이블에는 게시글이 1건뿐이었다 — MySQL 조회 자체가 이미 1ms 미만으로 끝나는 수준이라, DB를 거치는 경로와 캐시를 거치는 경로의 "원가" 차이가 거의 없었다.
- 반면 캐시 히트 한 번에는 Redis 왕복이 **2번** 필요하다(`BoardListCacheRepository.buildKey`가 버전 번호를 읽으려고 Redis GET을 먼저 하고, 그다음 실제 캐시 값을 또 Redis GET으로 읽음) + JSON 역직렬화(제네릭 `PageResponse<BoardListItemResponse>`를 리플렉션으로 조립) 비용이 추가된다.
- 즉 "이미 충분히 빠른 걸 캐싱하면 캐시 레이어 자체의 오버헤드(네트워크 왕복 + 직렬화)가 오히려 손해"라는, 캐싱에서 흔히 나오는 교훈을 이 프로젝트 데이터로 직접 확인한 셈이다.

**캐싱이 실제로 이득을 보려면**: 게시글 수가 훨씬 많아지거나(수천~수만 건, 인덱스를 타도 JOIN·서브쿼리 비용이 커짐), 동시 접속자가 많아져 DB 커넥션 풀 경합이 발생하거나, DB가 원격지에 있어 네트워크 지연이 커지는 상황에서 캐시의 이득이 커진다. 지금 이 프로젝트 규모에서는 캐싱 로직 자체(무효화, Redis 장애 대응, 히트/미스 전환)가 올바르게 동작하는 것이 확인된 것에 의의를 둔다 — 데이터가 늘어나면 이 가이드의 절차를 그대로 다시 돌려서 재측정해보면 된다.

## 참고: RedisInsight로 직접 확인하기
`http://localhost:5540`에서 `board-list:*` 키들을 직접 볼 수 있다. 요청을 보낼 때마다 키가 생기고, 게시글을 쓰거나 수정/삭제하면 `board-list:version` 값이 올라가면서 기존 키들이 더 이상 조회되지 않는 것을 확인할 수 있다.
