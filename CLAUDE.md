# Project: [게시판]

## Overview
Vibe Coding을 이용한 게시판 웹 애플리케이션

## Tech Stack
- Language: Java 17
- Framework:  Spring Boot 4.0.8
- Build Tool : Maven
- ORM : JPA
- SQL Mapper : Mybatis
- Security : Spring Security, JWT
- API : REST API
- Database: MySQL
- Cache / Session : Redis
- CI/CD : Docker, K8s

## Project Structure
```
src/
├── main/
│   ├── java/
│   │   └── .../
│   │       ├── controller/       # REST API Controller
│   │       ├── service/          # 비즈니스 로직
│   │       ├── repository/       # JPA Repository
│   │       ├── mapper/            # MyBatis Mapper
│   │       ├── entity/            # JPA Entity
│   │       ├── dto/               # Request / Response DTO
│   │       ├── config/            # Spring 설정
│   │       ├── security/          # 인증/인가 관련 코드
│   │       └── exception/         # 예외 처리
│   └── resources/
│       ├── application.yml        # 애플리케이션 설정
│       ├── mapper/                # MyBatis XML Mapper
│       └── ...
└── test/
    └── java/                      # 테스트 코드
```

---

## Development Workflow
- 모든 중간 규모 이상의 기능 개발은 다음 순서로 진행한다.
```text
1. 기존 코드와 프로젝트 구조를 분석한다.
2. 관련 Plan과 Work Log를 확인한다.
3. 필요한 경우 docs/plans/에 Plan을 작성하거나 기존 Plan을 업데이트한다.
4. 사용자에게 Plan을 제시하고 구현 승인을 기다린다.
5. 사용자가 승인한 경우에만 코드를 수정한다.
6. 구현 후 관련 테스트를 실행한다.
7. 실제 구현 결과를 Plan에 반영한다.
8. docs/work-log/에 실제 작업 내용을 기록한다.
```
### Important
- 사용자의 구현 승인 없이 Plan 단계에서 실제 코드를 수정하지 않는다.
- 단순한 오타 수정, 단순 버그 수정 등 작은 작업에는 별도의 Plan을 작성하지 않아도 된다.
- 사용자가 명시하지 않은 기능이나 요구사항을 임의로 추가하지 않는다.

---

## Documentation Rules
Plan과 Work Log는 서로 다른 목적으로 관리한다.

### Directory Structure

```
docs/
├── plans/          # 기능별 개발 계획
│   ├── 001-board-crud.md
│   ├── 002-comment.md
│   └── ...
└── work-log/       # 실제 작업 기록
    ├── 2026-08.md
    └── ...
```

## Plan
Plan은 **무엇을 어떻게 구현할 것인가**를 정의한다.

- Plan에는 다음 내용을 포함한다.
```text
- 작업 목적
- 요구사항
- 현재 구조 및 관련 코드
- 변경할 파일
- 구현 방법
- 데이터베이스 변경 사항
- API 변경 사항
- 테스트 계획
- 작업 상태
```

- Plan 상태는 다음 중 하나를 사용한다.
```text
- Planned
- In Progress
- Completed
- Cancelled
```

## Work Log
Work Log는 **실제로 무엇을 변경했는가**를 기록한다.

- Work Log에는 다음 내용을 포함한다.
```text
- 작업 날짜
- 작업 내용
- 변경된 파일
- 주요 변경사항
- 테스트 결과
- 발생한 문제 및 해결 방법
- Plan과 실제 구현의 차이점
```

## Plan / Work Log Rules
- Plan과 Work Log의 내용을 동일하게 복사하지 않는다.
- Plan은 구현 전에 작성하고, 구현 과정에서 변경사항이 발생하면 업데이트한다.
- 구현 완료 후 Plan의 상태를 Completed로 변경한다.
- 실제 코드 변경이 발생한 작업은 Work Log에 기록한다.
- 작업 시작 전에 관련 Plan과 기존 Work Log를 확인한다.
- 현재 코드와 기존 문서의 내용이 다르면 현재 코드를 기준으로 차이를 확인한다.

---

## Architecture Rules
- Controller는 HTTP 요청/응답 처리만 담당한다.
- 비즈니스 로직은 Service 계층에서 처리한다.
- 데이터베이스 접근은 Repository 또는 Mapper를 통해 처리한다.
- Entity를 API Response로 직접 반환하지 않는다.
- API 요청과 응답에는 DTO를 사용한다.
- Controller에서 직접 Repository 또는 Mapper를 호출하지 않는다.
- 하나의 클래스에 과도한 책임을 부여하지 않고 역할별로 분리한다.
- 기존 프로젝트의 패키지 구조와 설계 패턴을 우선적으로 따른다.

---

## JPA / MyBatis Rules
- 단순 CRUD 및 Entity 중심의 데이터 접근에는 JPA를 우선 사용한다.
- 복잡한 JOIN, 통계성 조회, 동적 SQL 등 JPA로 처리하기 적절하지 않은 경우 MyBatis를 사용한다.
- 동일한 기능에 대해 JPA와 MyBatis를 중복으로 사용하지 않는다.
- N+1 문제가 발생할 가능성이 있는 코드는 작성 전에 고려한다.
- 불필요한 Entity 조회를 최소화한다.
- 대량 데이터 조회 시 페이징을 고려한다.

---

## Code Style Rules
- Java 코드는 Java 17 문법을 기준으로 작성한다.
- 기존 코드의 네이밍 및 스타일을 우선적으로 따른다.
- 변수와 메서드 이름은 역할을 명확하게 표현한다.
- 불필요한 주석은 작성하지 않는다.
- 복잡한 비즈니스 로직에는 필요한 경우 주석을 추가한다.
- public 클래스 및 메서드 중 설명이 필요한 경우 JavaDoc을 작성한다.
- `System.out.println()`을 사용하지 않는다.
- 로그가 필요한 경우 프로젝트에서 사용하는 Logger를 사용한다.
- 사용하지 않는 import, 변수, 메서드는 남기지 않는다.
- 중복 코드를 가능한 한 제거한다.
- 예외를 무시하거나 빈 catch 블록을 작성하지 않는다.

---

## Database Rules
- 데이터베이스 변경이 필요한 경우 기존 스키마와 연관 관계를 먼저 확인한다.
- 기존 데이터에 영향을 줄 수 있는 SQL은 실행 전에 주의한다.
- DELETE, UPDATE 등 데이터 변경 작업은 영향 범위를 확인한다.
- 인덱스가 필요한 조회 조건을 고려한다.
- 트랜잭션이 필요한 비즈니스 로직에는 적절한 트랜잭션 처리를 적용한다.

---

## Redis Rules
- Redis는 캐싱, 세션, 인증 관련 데이터 등 적절한 용도로 사용한다.
- Redis에 저장되는 데이터는 필요한 경우 TTL을 명확하게 설정한다.
- Redis 장애가 서비스 전체 장애로 이어질 가능성을 고려한다.
- 캐시 데이터와 영속 데이터의 정합성 문제를 고려한다.

---

## Testing Rules
- 새로운 기능을 추가하거나 기존 기능을 수정할 경우 관련 테스트 코드를 작성한다.
- Service 계층의 핵심 비즈니스 로직은 단위 테스트를 우선적으로 작성한다.
- Controller는 API 동작과 HTTP Status를 검증한다.
- Repository 및 Mapper는 실제 데이터 접근이 필요한 경우 통합 테스트를 고려한다.
- 기존 테스트가 깨지는 경우 원인을 확인한 후 수정한다.
- 테스트를 통과하지 못한 상태에서 작업이 완료되었다고 판단하지 않는다.

---

## Git Rules
- 커밋 메시지는 한글로 작성한다.
- 커밋 메시지는 변경 내용을 명확하게 표현한다.
- 하나의 커밋에는 하나의 논리적인 변경사항을 포함한다.
- 작업과 관련 없는 파일을 임의로 수정하지 않는다.
- .env, 비밀번호, API Key 등의 민감한 정보를 커밋하지 않는다.
- 사용자의 명시적인 요청 없이 commit, push를 수행하지 않는다.


## Claude Code Rules
- 코드를 수정하기 전에 관련 파일과 기존 구현을 먼저 확인한다.
- 기존 코드를 충분히 확인하지 않고 새로운 구조를 임의로 만들지 않는다.
- 새로운 라이브러리나 의존성을 추가하기 전에 기존 의존성으로 해결할 수 있는지 확인한다.
- 사용자가 요청한 범위를 벗어난 리팩터링을 하지 않는다.
- 코드를 수정한 후 가능한 경우 관련 테스트를 실행한다.
- 오류가 발생하면 증상만 수정하지 말고 원인을 분석한다.
- 기존 기능에 영향을 줄 가능성이 있는 변경은 영향 범위를 먼저 확인한다.
- 요구사항이 불명확한 경우 임의로 결정하지 말고 사용자에게 질문한다.
- 작업 완료 후 관련 Plan과 Work Log를 업데이트한다.