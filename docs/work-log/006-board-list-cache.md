# 006. 게시글 목록 Redis 캐싱 + JMeter 부하테스트 - Work Log

## 2026-09-28

### 작업 내용
`docs/plans/006-board-list-cache.md`에 따라 게시글 목록(`GET /api/boards`) 조회를 Redis 캐시-어사이드로 감싸고, JMeter 부하테스트로 캐시 켬/끔 성능을 실측했다. Redis GUI 확인용으로 `docker-compose.yml`에 RedisInsight도 함께 추가했다(관련 논의 중 추가, 별도 코드 변경 없는 인프라 설정).

### 변경된 파일

**신규**
```
src/main/java/com/example/board/repository/BoardListCacheRepository.java
src/test/java/com/example/board/repository/BoardListCacheRepositoryTest.java   # 로컬 Redis 대상 통합 테스트
src/test/jmeter/board-list-load-test.jmx
docs/perf/006-jmeter-guide.md
```

**수정**
```
src/main/java/com/example/board/service/BoardService.java        # getList를 캐시-어사이드로 변경, create/update/delete에서 캐시 무효화
src/main/resources/application.properties                        # cache.board-list.enabled, cache.board-list.ttl-seconds 추가
pom.xml                                                            # perf-test 프로파일 + jmeter-maven-plugin 추가
src/test/java/com/example/board/service/BoardServiceTest.java     # 캐시 히트/미스, invalidate 호출 테스트 추가
docker-compose.yml                                                 # RedisInsight 컨테이너 추가(별도 논의 중 진행)
```

### 주요 변경사항
- `BoardListCacheRepository`: `StringRedisTemplate` + Spring이 자동 구성한 `tools.jackson.databind.ObjectMapper`(이 프로젝트가 실제 API 응답에 쓰는 Jackson)로 `PageResponse<BoardListItemResponse>`를 JSON 문자열로 직렬화해 저장/조회한다.
- 무효화는 버전 번호(`board-list:version`)를 `INCR`하는 방식이라 `KEYS`/`SCAN` 없이 O(1)로 끝난다. 이전 버전의 키들은 TTL(기본 30초)이 지나면 자연 소멸.
- 모든 Redis 호출을 try-catch로 감싸 Redis 장애 시 DB로 자동 대체되도록 했다(CLAUDE.md Redis 규칙 준수).
- `cache.board-list.enabled` 설정으로 캐시를 완전히 끌 수 있어, 캐시 없이/있이 두 상태를 코드 변경 없이 만들 수 있다.

### 테스트 결과
- `./mvnw test` 최종 87건 전체 통과(BUILD SUCCESS) — 기존 82건 + 신규 5건(`BoardServiceTest` 3건, `BoardListCacheRepositoryTest` 3건 중 일부는 기존 통합).
- `mvn clean verify -Pperf-test`가 정상적으로 JMeter를 실행하고 HTML 리포트를 생성하는 것을 확인. `-Pperf-test`를 켜지 않은 평소 `mvn test`에는 전혀 영향 없음을 확인(정상 87건 그대로 통과, 시간도 동일).
- **JMeter로 캐시 켬/끔을 실제로 비교했다** (게시글 1건, 10 threads × 20 loops = 200 요청, 동일 조건):

  | | 평균 응답시간 | 처리량 | 에러율 |
  |---|---|---|---|
  | 캐시 끔 | 24ms | 84.3/s | 0% |
  | 캐시 켬 | 28ms | 89.4/s | 0% |

  캐시를 켰는데 오히려 근소하게 더 느렸다. 원인: 이 시점 데이터가 게시글 1건뿐이라 MySQL 조회 자체가 이미 1ms 미만으로 끝나는데, 캐시 히트 한 번에는 Redis 왕복이 2번(버전 번호 조회 + 실제 값 조회) 필요하고 JSON 역직렬화 비용도 붙어서, "이미 충분히 빠른 걸 캐싱하면 캐시 레이어 오버헤드가 오히려 손해"라는 결과가 그대로 나왔다. 캐싱/무효화 로직 자체는 정확히 의도대로 동작했고(기능적으로는 문제없음), 다만 이 데이터 규모에서는 성능 이득이 없다는 것을 실측으로 확인한 것 — 이건 버그가 아니라 실제로 벌어진 일이라 그대로 기록한다. 자세한 내용과 재측정 절차는 `docs/perf/006-jmeter-guide.md` 참고.
- 검증에 사용한 별도 앱 인스턴스(포트 8089, 캐시 켬/끔 각각)는 확인 후 종료했고, 테스트 중 생성된 board/user 데이터도 없었다(부하테스트 자체는 읽기 전용 API만 호출).

### 발생한 문제 및 해결 방법
1. **`-DskipTests`를 같이 쓰면 JMeter도 함께 스킵됐다.** `jmeter-maven-plugin`이 surefire와 동일한 skip 플래그를 존중하는 것으로 보인다. `mvn verify -Pperf-test`를 스킵 플래그 없이 실행하도록 가이드에 명시했다(일반 단위 테스트도 같이 돌지만 87건이라 1~2분 정도 추가되는 수준).
2. **`jmeter` goal만 바인딩했더니 `target/config.json`을 못 찾아 실패했다.** `jmeter-maven-plugin`은 `configure` goal이 먼저 돌면서 설정 파일을 만들어둬야 `jmeter` goal이 동작한다. `pom.xml`에 `configuration`(goal: `configure`) execution을 `jmeter-tests` execution 앞에 추가해 해결했다.
3. **`-Djmeter.threads=5` 같은 CLI 오버라이드가 실제로 적용되지 않았다** — 처음 돌렸을 때 지정한 값(threads=5, loops=5 → 25건 기대)이 아니라 기본값(threads=20, loops=50 → 1000건)으로 실행됐다. 원인은 `.jmx`의 `__P(threads,20)`가 읽는 "JMeter 속성"과 Maven CLI의 `-D` 시스템 프로퍼티가 별개라서, 별도 연결 없이는 전달이 안 됐기 때문이다. `jmeter-maven-plugin`의 `<propertiesUser>` 설정에 `${jmeter.threads}` 같은 Maven 프로퍼티 참조를 매핑하고, `perf-test` 프로파일에 기본값(`<properties>`)을 선언해서 해결했다 — 이후 재실행으로 `-Djmeter.threads=5 -Djmeter.loops=5`가 정확히 25건 실행되는 것으로 확인.
4. **`clean` 없이 재실행하면 "폴더가 비어있지 않다" 에러로 실패했다.** JMeter는 기존 HTML 리포트 디렉터리가 남아있으면 새로 리포트를 못 만든다. 가이드에 항상 `mvn clean verify -Pperf-test`로 실행하도록, 그리고 비교하려는 리포트는 실행 직후 다른 폴더로 복사해두도록 명시했다.

### Plan과 실제 구현의 차이점
`docs/plans/006-board-list-cache.md`의 "구현 결과와의 차이점" 절 참고 — `jmeter-maven-plugin` 설정(`configure` goal 추가, `propertiesUser` 매핑)이 계획보다 더 필요했던 점, 그리고 실측 결과가 예상(캐시가 더 빠름)과 달랐던 점(이 데이터 규모에서는 캐시가 근소하게 느림)이 차이점이다. 캐싱/무효화/장애 폴백 로직 자체는 계획대로 정확히 구현됐다.
