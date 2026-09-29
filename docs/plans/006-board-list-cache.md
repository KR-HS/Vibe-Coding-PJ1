# 006. 게시글 목록 Redis 캐싱 + JMeter 부하테스트

## 작업 목적
CLAUDE.md 기술 스택에 Redis가 "캐싱, 세션"용으로 명시되어 있지만, 지금까지 Redis는 Refresh Token 저장(`RefreshTokenRepository`)에만 쓰였고 실제 캐싱(자주 조회되는 데이터를 DB 대신 Redis에서 읽는 것)은 한 번도 쓰이지 않았다. 이번 Plan에서 가장 자주 조회되는 데이터인 **게시글 목록**(`GET /api/boards`)을 Redis로 캐싱하고, **JMeter 부하테스트**로 캐싱 전/후 성능을 실측해서 비교한다.

캐싱 자체는 "체감상 빨라진 것 같다"로 끝나기 쉬운데, JMeter로 동일한 시나리오(동시 사용자 수, 반복 횟수)를 캐시 켠 상태/끈 상태 양쪽에서 돌려 평균 응답시간·처리량(TPS)·에러율을 숫자로 비교하는 것이 이번 Plan의 핵심이다.

## 요구사항
- [x] 게시글 목록 조회(`GET /api/boards`) 결과를 Redis에 캐싱해, 같은 조건(키워드/카테고리/페이지)으로 짧은 시간 내 재조회하면 DB를 거치지 않는다
- [x] 게시글 작성/수정/삭제 시 목록 캐시를 무효화한다
- [x] Redis 장애 시에도 게시글 목록 조회 자체는 실패하지 않고 DB로 자동 대체된다(CLAUDE.md Redis 규칙 준수)
- [x] 캐시를 켜고 끌 수 있는 설정값을 둬서, 같은 코드로 "캐시 있음/없음" 두 상태를 만들 수 있다(JMeter 비교의 전제조건)
- [x] JMeter 테스트 계획(.jmx)을 리포지토리에 포함해, 누구나 로컬에서 동일한 시나리오로 부하테스트를 재현할 수 있다
- [x] `mvn verify -Pperf-test` 같은 명령으로 JMeter를 실행하고 HTML 리포트를 생성할 수 있다(평소 `mvn test`에는 영향 없음)

### 확인된 사용자 결정 사항
- 캐싱 대상은 **일반 게시글 목록(`BoardService.getList`)만** 다룬다. `getMyList`/`getLikedList`는 사용자별로 갈리고 조회 빈도가 낮아 제외.
- 캐시 무효화는 게시글 작성/수정/삭제 시에만 수행한다. 좋아요/댓글로 인한 카운트 변화는 캐시를 매번 비우지 않고 TTL(기본 30초)로 자연 갱신되게 둔다 — 매번 지우면 캐싱 효과 자체가 사라지기 때문. TTL은 설정으로 조정 가능.
- 캐시 무효화는 **버전 번호 방식**을 쓴다(Redis `KEYS`/`SCAN` 없이 `INCR` 한 번으로 기존 키를 전부 "안 쓰는 키"로 만듦, TTL 지나면 자연 소멸).
- **성능 확인은 애플리케이션 내부 통계 화면이 아니라 JMeter 부하테스트로 한다**(직전 논의에서 관리자 화면에 히트/미스 통계를 넣는 방향으로 진행하려 했으나, 사용자가 "전체적인 성능체크"와 JMeter를 원한다고 정정함 — 그래서 이번 Plan은 관리자 통계 화면 대신 JMeter 테스트 계획을 산출물로 한다).
- JMeter는 Maven 빌드에 통합하되(`jmeter-maven-plugin`, 재현성 확보), **기본 빌드 생명주기(`mvn test`, `mvn install`)에는 절대 끼워 넣지 않는다** — 별도 Maven 프로파일(`perf-test`)로 분리해서 명시적으로 실행할 때만 동작하게 한다. 평소 테스트가 느려지거나 CI 동작이 바뀌면 안 되기 때문.

## 현재 구조 및 관련 코드
- `RefreshTokenRepository`가 Redis 사용의 유일한 예시다(`StringRedisTemplate`, 문자열 값만 다룸). 이번엔 `PageResponse<BoardListItemResponse>` 객체를 캐싱해야 하므로 JSON 직렬화가 필요하다.
- **이 프로젝트는 Jackson 3(`tools.jackson.*`)와 Jackson 2(`com.fasterxml.jackson.*`)가 동시에 클래스패스에 있다**(`mvn dependency:tree` 확인: `tools.jackson.core:jackson-databind:3.1.5`는 `compile` 스코프, `com.fasterxml.jackson.core:jackson-databind:2.21.5`는 `runtime` 스코프). REST API 응답에 실제로 쓰이는 건 `tools.jackson.databind.ObjectMapper`이므로(`AuthControllerTest`에서 이미 이 타입 사용 중), 캐시 직렬화도 같은 `ObjectMapper`(Spring이 자동 구성한 빈)를 주입받아 쓴다.
- `BoardService.getList(keyword, category, page, size)`가 `BoardMapper.findList`/`count`(MyBatis)로 DB에서 직접 조회한다. 캐시-어사이드(cache-aside) 패턴으로 감싼다.
- CLAUDE.md Redis 규칙("Redis 장애가 서비스 전체 장애로 이어질 가능성을 고려한다")에 따라 캐시 읽기/쓰기를 try-catch로 감싸 장애 시 DB로 자연스럽게 넘어가게 한다.
- **JMeter는 이 프로젝트에 처음 도입되는 도구다.** Maven 통합은 `com.lazerycode.jmeter:jmeter-maven-plugin`을 쓰고, `.jmx` 테스트 계획은 플러그인 기본 규칙에 따라 `src/test/jmeter/`에 둔다. `pom.xml`의 `<profiles>`에 `perf-test` 프로파일을 추가해, 이 플러그인의 goal(`jmeter`)이 그 프로파일 안에서만 `verify` 단계에 바인딩되도록 한다 — 기본 `mvn test`/`mvn install`에는 전혀 관여하지 않는다.
- 테스트 대상 서버는 로컬에서 실제로 떠 있어야 한다(JMeter는 HTTP 요청을 실제로 보내는 블랙박스 부하테스트 도구라 애플리케이션 컨텍스트를 직접 띄우지 않음) — 실행 전 `mvn spring-boot:run` 등으로 앱이 떠 있어야 한다는 전제를 문서화한다.

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/repository/BoardListCacheRepository.java   # Redis 캐시 읽기/쓰기/버전 무효화

src/test/jmeter/board-list-load-test.jmx   # 게시글 목록 조회 부하테스트 계획
docs/perf/006-jmeter-guide.md               # 실행 방법, 캐시 on/off 비교 절차, 결과 해석 가이드
```

### 수정
```
src/main/java/com/example/board/service/BoardService.java        # getList를 캐시-어사이드로 변경, create/update/delete에서 캐시 무효화
src/main/resources/application.properties                        # cache.board-list.enabled, cache.board-list.ttl-seconds 설정 추가
pom.xml                                                            # jmeter-maven-plugin을 perf-test 프로파일에 추가

src/test/java/com/example/board/service/BoardServiceTest.java
src/test/java/com/example/board/repository/... (신규 BoardListCacheRepositoryTest, 로컬 Redis 대상 통합 테스트)
```

## 구현 방법

### 1. `BoardListCacheRepository`
```java
@Repository
@RequiredArgsConstructor
public class BoardListCacheRepository {
    private static final String VERSION_KEY = "board-list:version";
    private static final String KEY_PREFIX = "board-list:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper; // tools.jackson.databind.ObjectMapper
    private final BoardCacheProperties properties; // enabled, ttl

    public Optional<PageResponse<BoardListItemResponse>> find(String keyword, BoardCategory category, int page, int size) {
        if (!properties.isEnabled()) return Optional.empty();
        try {
            String value = redisTemplate.opsForValue().get(buildKey(keyword, category, page, size));
            return value == null ? Optional.empty() : Optional.of(deserialize(value));
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 조회 실패, DB로 대체합니다", e);
            return Optional.empty();
        }
    }

    public void save(String keyword, BoardCategory category, int page, int size, PageResponse<BoardListItemResponse> response) {
        if (!properties.isEnabled()) return;
        try {
            redisTemplate.opsForValue().set(buildKey(keyword, category, page, size), serialize(response), properties.getTtl());
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 저장 실패", e);
        }
    }

    public void invalidate() {
        try {
            redisTemplate.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("게시글 목록 캐시 무효화 실패", e);
        }
    }
    // buildKey()는 VERSION_KEY 조회 후 "board-list:{version}:{page}:{size}:{keyword}:{category}" 형태로 구성
}
```
- `cache.board-list.enabled=false`로 설정하면 캐시를 아예 타지 않고 매번 DB로 간다 — JMeter로 "캐시 없음" 시나리오를 만들 때 코드 변경 없이 설정 하나로 전환하기 위함.

### 2. `BoardService.getList` 캐시-어사이드 적용
- 캐시 히트 시 `boardMapper` 호출 없이 바로 반환, 미스 시 기존 로직대로 조회 후 캐시에 저장.
- `create`/`update`/`delete` 끝에 `boardListCacheRepository.invalidate()` 호출 추가.

### 3. `application.properties`
```properties
# Board list cache (Redis)
cache.board-list.enabled=true
cache.board-list.ttl-seconds=30
```

### 4. JMeter 테스트 계획 (`src/test/jmeter/board-list-load-test.jmx`)
- Thread Group: User Defined Variables로 동시 사용자 수(`THREADS`, 기본 20), 반복 횟수(`LOOPS`, 기본 50), ramp-up 시간을 파라미터화(커맨드라인에서 `-JTHREADS=50` 식으로 덮어쓸 수 있게).
- HTTP Request: `GET http://localhost:${__P(port,8081)}/api/boards?page=0&size=10` (페이지/사이즈 고정 — 같은 키에 캐시가 반복적으로 히트하도록).
- Response Assertion: HTTP 200 확인.
- Summary Report / Aggregate Report 리스너: 평균/최소/최대 응답시간, 처리량(TPS), 에러율 집계.
- `pom.xml`의 `jmeter-maven-plugin`이 `target/jmeter/results`에 `.jtl` 결과와 HTML 리포트를 생성하도록 설정(`<generateReports>true</generateReports>`).

### 5. `pom.xml` — `perf-test` 프로파일
```xml
<profiles>
    <profile>
        <id>perf-test</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>com.lazerycode.jmeter</groupId>
                    <artifactId>jmeter-maven-plugin</artifactId>
                    <version>3.8.0</version>
                    <executions>
                        <execution>
                            <id>jmeter-tests</id>
                            <phase>verify</phase>
                            <goals><goal>jmeter</goal></goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```
- 이 프로파일이 활성화되지 않으면(`mvn test`, `mvn install` 등 평소 명령) `jmeter-maven-plugin`은 전혀 실행되지 않는다.

### 6. 실행/비교 가이드 (`docs/perf/006-jmeter-guide.md`)
- 앱을 로컬에서 띄운 상태에서:
  1. `cache.board-list.enabled=false`로 두고 `mvn verify -Pperf-test` 실행 → 리포트 저장(캐시 없음 기준선).
  2. `cache.board-list.enabled=true`로 바꾸고 앱 재시작 후 동일하게 실행 → 리포트 저장(캐시 있음).
  3. 두 HTML 리포트의 평균 응답시간·TPS를 비교해서 차이를 수치로 확인.
- 캐시 히트가 잘 나오려면 JMeter가 같은 페이지/사이즈/키워드 조합으로 반복 요청해야 한다는 점(다른 조합마다 캐시 키가 달라짐)을 가이드에 명시.

## 데이터베이스 변경 사항
없음(Redis 키만 추가, RDB 스키마 변경 없음).

## API 변경 사항
없음. `GET /api/boards`의 요청/응답 스펙은 그대로이며 내부 동작만 캐시-어사이드로 바뀐다.

## 테스트 계획
- **Service 단위 테스트**: `BoardServiceTest`에 캐시 히트 시 `boardMapper` 미호출, 캐시 미스 시 DB 조회 후 캐시 저장, `create`/`update`/`delete` 후 무효화 호출, `cache.board-list.enabled=false`일 때 캐시를 아예 타지 않는지 확인.
- **Repository 통합 테스트**: `BoardListCacheRepositoryTest`(로컬 Docker Redis 대상)에서 저장 후 조회 성공, `invalidate()` 후 이전 키로 조회 시 미스 처리 확인.
- **JMeter**: `mvn verify -Pperf-test` 실행이 정상적으로 리포트를 생성하는지 1회 확인(테스트 스위트에 자동 포함되지는 않음 — 수동 실행 항목).
- Redis 장애 시나리오는 로컬 Docker Redis 컨테이너를 잠깐 내렸다가 `GET /api/boards` 호출이 여전히 200으로 응답하는지 수동으로 확인한다.
- 전체 구현 후 `./mvnw test`로 기존 테스트 포함 전체 회귀 확인(JMeter는 `perf-test` 프로파일에서만 도니 여기 포함 안 됨).

## 작업 상태
- Completed

### 구현 결과와의 차이점
- 설계(캐시-어사이드, 버전 번호 무효화, Redis 장애 폴백, `cache.board-list.enabled` 토글, `.jmx` 테스트 계획, `perf-test` 프로파일)는 계획대로 구현했다.
- `pom.xml`의 `jmeter-maven-plugin` 설정을 계획보다 조금 더 채워야 했다 — 실제로 돌려보니 두 가지가 빠져 있었다(둘 다 Work Log에 원인/해결 기록):
  1. `configure` goal 실행이 없으면 `jmeter` goal이 `target/config.json`을 못 찾아 실패한다 — `configuration` execution을 추가했다.
  2. `-Djmeter.threads=5` 같은 CLI 오버라이드가 `.jmx`의 `__P()`에 전달되지 않았다 — `propertiesUser` 설정과 프로파일 전용 기본값(`<properties>`)을 추가해서 해결했다.
- 계획에는 없었지만, 실제로 JMeter를 캐시 켬/끔 두 번 돌려서 **진짜 숫자로 비교**했다(Work Log·`docs/perf/006-jmeter-guide.md` 참고). 이 프로젝트의 현재 데이터 규모(게시글 1건)에서는 캐시가 오히려 근소하게 더 느리게 나왔다 — 예상과 다른 결과였지만 숨기지 않고 그대로 기록했다(원인 분석 포함).
