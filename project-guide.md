# Kafka 테스트 프로젝트 가이드

> Claude가 이 문서를 읽고 프로젝트 구조, 파일 역할, 구현 지침을 파악하여 일관된 방식으로 작업합니다.

---

## 프로젝트 전체 구조

```
초기 테스트/
├── kafka-test1/          → Spring Boot (Producer 서버, 포트 8080)
├── kafka-test2/          → Spring Boot (Consumer 서버, 포트 8081)
├── kafka-frontend/       → React + Vite (프론트엔드, 포트 5173)
├── project-guide.md      → 이 파일 (프로젝트 전체 지침)
└── kafka-install-guide.md → Kafka VM 설치 가이드 (참고용)
```

---

## 서비스 역할

```
[React]
  └─ 주문 입력 폼 / 주문 목록 화면
  └─ test1으로 HTTP 요청

[kafka-test1] Producer 서버
  └─ 주문 접수 REST API
  └─ DB에 PENDING 상태로 저장
  └─ Kafka order-events 토픽에 메시지 발행

[Kafka Broker] VM (RHEL)
  └─ 토픽: order-events (파티션 3개)
  └─ 주소: localhost:9092

[kafka-test2] Consumer 서버
  └─ Kafka 메시지 수신
  └─ DB에 PROCESSED 상태로 업데이트
  └─ 주문 조회 API 제공

[MySQL]
  └─ DB: kafka_test
  └─ 테이블: orders
```

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Spring Boot | 4.0.5 |
| Java | 21 |
| Kafka | 3.9.2 |
| MySQL | 8.0 |
| React | (Vite 최신) |

---

## DB 테이블 구조

```sql
CREATE TABLE orders (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product       VARCHAR(100) NOT NULL,
    quantity      INT          NOT NULL,
    price         INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    kafka_offset  BIGINT,
    failed_reason VARCHAR(255),
    created_at    DATETIME     NOT NULL,
    processed_at  DATETIME
);
```

**status 흐름**
```
PENDING   → test1이 Kafka 발행 완료
PROCESSED → test2가 메시지 수신 후 저장 완료
FAILED    → test2 처리 실패
```

---

## 파일별 역할

### kafka-test1 (Producer)

| 파일 | 역할 |
|------|------|
| `build.gradle` | spring-kafka, lombok, jpa, mysql 의존성 |
| `application.yaml` | DB/Kafka 연결 설정 |
| `Order.java` | orders 테이블 Entity |
| `OrderRepository.java` | JpaRepository — DB CRUD |
| `OrderEvent.java` | Kafka 발행용 메시지 DTO |
| `OrderProducer.java` | pause 시 Redis 버퍼, resume 시 drain → Kafka 발행, KAFKA_SENT 상태 업데이트 |
| `RedisMessageBuffer.java` | RedisTemplate — Redis List 기반 버퍼 (push/pop/size/getAll/clear) |
| `OrderController.java` | REST API (주문 CRUD, pause/resume/status, buffer 조회/삭제) |

### kafka-test2 (Consumer)

| 파일 | 역할 |
|------|------|
| `build.gradle` | spring-kafka, lombok, jpa, mysql 의존성 |
| `application.yaml` | DB/Kafka 연결 설정 (포트 8081) |
| `Order.java` | orders 테이블 Entity (test1과 동일) |
| `OrderRepository.java` | JpaRepository — DB CRUD |
| `OrderConsumer.java` | @KafkaListener로 order-events 수신 후 DB 저장 |
| `OrderController.java` | 조회 전용 REST API (GET /api/orders) |

### kafka-frontend (React)

| 파일 | 역할 |
|------|------|
| `src/App.jsx` | 주문 폼 + 주문 목록 화면 (상태 필터 탭, 페이지네이션, 최신순 정렬, Redis 버퍼 표시) |
| `src/App.css` | 스타일 (테이블 스크롤, 탭, 페이지네이션, 빈 상태) |
| `src/api/orderApi.js` | axios 기반 API 호출 함수 모음 |
| `vite.config.js` | 프록시 설정 (CORS 우회) |

---

## API 명세

### kafka-test1 (8080)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/health` | 헬스 체크 |
| GET | `/api/orders` | 전체 주문 목록 |
| GET | `/api/order/{id}` | 단건 주문 조회 |
| POST | `/api/order` | 주문 생성 + Kafka 발행 (pause 시 Redis 버퍼) |
| POST | `/api/producer/pause` | Producer 중지 |
| POST | `/api/producer/resume` | Producer 재개 + Redis 버퍼 drain |
| GET | `/api/producer/status` | 상태 조회 `{paused, bufferSize}` |
| GET | `/api/producer/buffer` | Redis 대기 메시지 수 + 목록 |
| DELETE | `/api/producer/buffer` | Redis 버퍼 수동 비우기 |

**POST /api/order 요청 예시**
```json
{
  "product": "사과",
  "quantity": 2,
  "price": 3000
}
```

**GET /api/producer/buffer 응답 예시**
```json
{
  "count": 3,
  "messages": [
    { "orderId": 12, "product": "사과", "quantity": 2, "price": 3000 }
  ]
}
```

### kafka-test2 (8081)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/health` | 헬스 체크 |
| GET | `/api/orders` | 전체 주문 목록 |
| GET | `/api/order/{id}` | 단건 주문 조회 |
| GET | `/api/orders/status/{status}` | 상태별 주문 조회 |
| POST | `/api/consumer/pause` | Consumer 중지 |
| POST | `/api/consumer/resume` | Consumer 재개 |
| GET | `/api/consumer/status` | 상태 + LAG 조회 `{paused, lag}` |

---

## 환경변수 설정

application.yaml은 모든 민감 정보를 환경변수로 관리합니다.
`.env` 파일은 gitignore에 포함되어 있으므로 직접 생성해서 사용합니다.

**.env.example 참고하여 .env 파일 생성**
```bash
# 초기 테스트/ 루트에 .env 파일 생성
DB_HOST=localhost
DB_NAME=kafka_test
DB_USER=root
DB_PASS=paxp

KAFKA_HOST=localhost   # VM 연결 시 VM의 IP로 변경

REDIS_HOST=localhost   # Docker 배포 시 redis로 변경
REDIS_PORT=6379
```

**IDE(IntelliJ)에서 환경변수 설정하는 방법**
```
Run/Debug Configurations → Environment variables 항목에 위 변수들 입력
또는 EnvFile 플러그인 사용 시 .env 파일 경로 지정
```

**주의사항**
- `.env` 파일은 절대 커밋하지 않음
- `.env.example`만 커밋 (실제 값 없이 키만 명시)
- 새로운 환경변수 추가 시 `.env.example`과 이 문서에 반드시 반영

---

## Kafka 설정

| 항목 | 값 |
|------|-----|
| 브로커 주소 | `localhost:9092` (로컬) / VM IP:9092 (VM 연결 시) |
| 토픽 | `order-events` |
| 파티션 | 3 |
| Consumer Group | `order-group` |

---

## 브랜치 전략

```
main  → 최종 완성 버전만 유지 (직접 커밋 금지)
dev   → 모든 작업 브랜치
```

**작업 흐름**
```
dev 에서 작업 → 커밋 → 완성 시 main 머지
```

---

## Claude 구현 지침

### 반드시 따를 것

1. **코드 스타일**
   - SLF4J 로깅: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class)`
   - 로그 형식: `logger.info("message {}", variable)`
   - 에러 처리: `ResponseStatusException(HttpStatus.XXX, "message")`
   - toString: `String.format()` 사용

2. **Lombok 사용**
   - Entity, DTO에 `@Getter @Setter` 사용
   - toString은 직접 작성 (String.format 유지)

3. **Spring Boot 4.x 호환성**
   - `jakarta.persistence.*` 사용 (javax 금지)
   - `JpaRepository` 사용 (`CrudRepository` 금지)
   - `findById()` 반환 타입은 `Optional` → `.orElseThrow()` 처리

4. **파일 작성 순서**
   ```
   build.gradle → application.yaml → Entity → Repository → 비즈니스 클래스 → Controller
   ```

5. **커밋은 dev 브랜치에**
   - 커밋 메시지 형식: `feat: 설명`, `fix: 설명`, `refactor: 설명`

### 하지 말 것

- `javax.persistence.*` 사용 금지
- `CrudRepository` 사용 금지
- Lombok 없이 getter/setter 직접 작성 금지
- main 브랜치에 직접 커밋 금지
- `spring-boot-starter-webmvc` 사용 금지 → `spring-boot-starter-web` 사용

---

## 구현 진행 현황

- [x] Kafka VM 설치 (RHEL)
- [x] kafka-test1 Producer 구현
  - [x] build.gradle (kafka, lombok 추가)
  - [x] application.yaml
  - [x] Order.java (Entity)
  - [x] OrderRepository.java
  - [x] OrderEvent.java (DTO)
  - [x] OrderProducer.java (pause/resume 플래그 포함)
  - [x] OrderController.java (pause/resume/status API 포함)
  - [x] KafkaProducerConfig.java
- [x] kafka-test2 Consumer 구현
  - [x] build.gradle
  - [x] application.yaml
  - [x] Order.java (Entity)
  - [x] OrderRepository.java
  - [x] OrderConsumer.java
  - [x] OrderController.java (pause/resume/status API 포함)
  - [x] KafkaConsumerConfig.java
  - [x] ConsumerControlService.java (KafkaListenerEndpointRegistry + AdminClient LAG 조회)
- [x] kafka-frontend React 구현
  - [x] 주문 폼 (상품명, 수량, 가격)
  - [x] 랜덤 주문 전송 (개수 지정)
  - [x] Producer 주문 목록 (test1 - 8080)
  - [x] Consumer 처리 목록 (test2 - 8081)
  - [x] API 연동 (axios)
  - [x] vite.config.js 프록시 설정
  - [x] Kafka 제어판 (Producer/Consumer 상태 + 중지/재개 버튼 + LAG 실시간 표시)
  - [x] 주문 목록 UX 개선
    - [x] 최신순 정렬 (ID 내림차순)
    - [x] 상태 필터 탭 (전체 / PENDING / KAFKA_SENT / PROCESSED / FAILED) — Producer만
    - [x] 페이지네이션 (10건씩, 처음/이전/다음/끝 버튼)
    - [x] 테이블 스크롤 (max-height 400px)
    - [x] 빈 상태 메시지 ("데이터 없음")
    - [x] 총 건수 표시
  - [x] Kafka CLI 실습 패널 (오른쪽 사이드바)
    - [x] 2컬럼 레이아웃 (메인 콘텐츠 + 우측 sticky 패널)
    - [x] Bootstrap Server 입력창 — 실시간으로 모든 명령어 주소 반영
    - [x] 카테고리별 탭 네비게이션 (9개 탭: 토픽 확인/관리, 구독, 발행, 파티션 분산, LAG, Rebalancing, Offset 리셋, 성능 테스트)
    - [x] 복사 버튼 — 클릭 시 1.5초간 "복사됨" 표시
    - [x] 다크 테마 코드블록, 1100px 이하 반응형 처리
- [x] Redis 버퍼 패턴 구현 (1단계 완료)
  - [x] Redis 로컬 설치
  - [x] test1 `build.gradle` — `spring-boot-starter-data-redis` 추가
  - [x] test1 `application.yaml` — `REDIS_HOST:localhost`, `REDIS_PORT:6379` 환경변수 추가
  - [x] `RedisMessageBuffer.java` — push/pop/size/getAll/clear (Redis List FIFO)
  - [x] `OrderProducer.java` — pause 시 Redis push, resume 시 Redis drain → Kafka, Kafka 성공 시 `KAFKA_SENT` 업데이트
  - [x] `OrderController.java` — `GET /api/producer/buffer`, `DELETE /api/producer/buffer` 추가
  - [x] `orderApi.js` — `getProducerBuffer()`, `clearProducerBuffer()` 추가
  - [x] `App.jsx` — Redis 버퍼 실시간 테이블 + 버퍼 비우기 버튼 (3초 폴링)
  - [x] `App.css` — `KAFKA_SENT` 파란색, 버퍼 패널 스타일 추가
- [x] 주문 상태 3단계 개선 (Redis 버퍼 구현과 함께 완료)
  - [x] `PENDING` → DB 저장됨, Kafka 미발행 (Producer 중지 상태)
  - [x] `KAFKA_SENT` → Kafka 발행 완료, Consumer 처리 대기 중 (파란색)
  - [x] `PROCESSED` → Consumer 처리 완료 (녹색)
- [x] 통합 테스트 (2026-04-03 완료)
  - [x] 시나리오 1: 기본 메시지 흐름 — PENDING → KAFKA_SENT → PROCESSED 정상
  - [x] 시나리오 2: Consumer 중지 → LAG 5 쌓임 → 재개 → 일괄 처리 확인 — LAG 0으로 복구
  - [x] 시나리오 3: Producer 중지 → Redis 버퍼 3건 쌓임 → 재개 → drain 확인 — bufferSize 0으로 복구, 전부 PROCESSED
  - [ ] 시나리오 4: Consumer Group 테스트
  - [ ] 시나리오 5: 장애 상황 테스트

### 통합 테스트 중 발견된 버그 및 수정 내역 (2026-04-03)

| # | 파일 | 문제 | 수정 내용 |
|---|------|------|----------|
| 1 | `kafka-test1/OrderController.java` | `getBuffer()` 메서드의 `Map.class` 타입 추론 실패 — Java 컴파일 에러 | `@SuppressWarnings("unchecked")` + 명시적 `Map<String, Object>` 캐스팅으로 수정 |
| 2 | `kafka-test1/KafkaProducerConfig.java` | Spring Boot 4.x에서 `ObjectMapper` 빈이 자동 등록되지 않아 앱 기동 실패 (`UnsatisfiedDependencyException`) | `KafkaProducerConfig`에 `@Bean ObjectMapper` 수동 등록 + `findAndRegisterModules()` 적용 |

**Kafka VM IP**: `192.168.153.128:9092` (VMware VM, MAC: 00-0c-29)

---

## 이후 진행 예정

### ~~Redis 버퍼 패턴 구현~~ ✅ 완료
> 구현 완료 — 구현 진행 현황 참고

### ~~주문 상태 3단계 개선~~ ✅ 완료
> 구현 완료 — 구현 진행 현황 참고

---

### Docker Compose 통합 배포 ✅ 구성 완료

**서비스 아키텍처**
```
[브라우저]
    │
    ▼
[nginx:80] ── kafka-frontend (React 빌드 결과물 서빙)
    │
    ├─ /api/producer/* → kafka-test1:8080
    └─ /api/consumer/* → kafka-test2:8081

[kafka-test1:8080] ─── mysql:3307 (호스트 포트)
        │               redis:6380 (호스트 포트)
        └─ publish ──→ kafka:9092 (내부) / 29092 (CLI용 외부)

[kafka-test2:8081] ─── mysql:3307
        └─ consume ──← kafka:9092

[kafka:9092/29092] ← KRaft 모드 (Zookeeper 없음)
[mysql:3306/3307]
[redis:6379/6380]
```

**Docker Compose 구성 파일**
```
초기 테스트/
├── docker-compose.yml    ← 기본값 내장 — .env 없어도 바로 실행 가능
├── .env.example          ← 커밋됨. 복사 후 .env 로 사용 (옵션)
├── .env                  ← gitignore. IntelliJ 로컬 개발 + Docker 오버라이드 겸용
├── kafka-test1/
│   ├── Dockerfile        ← eclipse-temurin:21 멀티스테이지
│   └── .dockerignore
├── kafka-test2/
│   ├── Dockerfile
│   └── .dockerignore
└── kafka-frontend/
    ├── Dockerfile        ← node:20 빌드 → nginx:alpine 서빙
    ├── nginx.conf        ← /api/producer/ → test1, /api/consumer/ → test2
    └── .dockerignore
```

**환경변수 전략**
- `docker-compose.yml`에 기본값 내장 (`${VAR:-default}` 형태)
- `DB_HOST / KAFKA_HOST / REDIS_HOST`는 compose 내부에서 서비스명으로 고정 (`.env` 충돌 없음)
- `.env`가 있으면 `DB_PASS`, `KAFKA_EXTERNAL_HOST` 등을 오버라이드

**API 라우팅 (로컬 개발 ↔ Docker 동일)**
| 환경 | Producer 경로 | Consumer 경로 |
|------|--------------|--------------|
| 로컬 개발 (Vite proxy) | `/api/producer/...` → rewrite → `localhost:8080/...` | `/api/consumer/...` → rewrite → `localhost:8081/...` |
| Docker (nginx) | `/api/producer/...` → nginx → `kafka-test1:8080/...` | `/api/consumer/...` → nginx → `kafka-test2:8081/...` |

**작업 완료 현황**
- [x] `kafka-test1/Dockerfile` (eclipse-temurin:21 멀티스테이지)
- [x] `kafka-test2/Dockerfile`
- [x] `kafka-frontend/Dockerfile` (node:20 빌드 → nginx:alpine)
- [x] `kafka-frontend/nginx.conf`
- [x] `docker-compose.yml` (기본값 내장, `.env` 없이 실행 가능)
- [x] `.env.example` (IntelliJ + Docker 겸용 설명)
- [x] `orderApi.js` 상대경로 전환 (`/api/producer`, `/api/consumer`)
- [x] `vite.config.js` prefix rewrite 프록시
- [ ] ESXi VM에서 `docker compose up --build` 테스트

---

## Docker Compose 실행 가이드

### 로컬 (바로 실행)
```bash
# git clone 후 즉시 실행 가능 — .env 없어도 기본값으로 동작
docker compose up --build

# 접속
# 프론트엔드:    http://localhost
# Kafka CLI:    Bootstrap Server → localhost:29092

# 중지
docker compose down
```

### ESXi VM 배포

```bash
# 1. GitHub에서 클론
git clone <repo-url>
cd <repo-dir>

# 2. .env 생성 (비밀번호 변경 또는 KAFKA_EXTERNAL_HOST 설정 시)
cp .env.example .env
# .env 편집:
#   DB_PASS=실제비밀번호
#   KAFKA_EXTERNAL_HOST=VM의IP   ← CLI 패널에서 외부 접속 시 필요

# 3. 빌드 및 실행
docker compose up --build -d

# 4. 접속
# 프론트엔드:    http://<VM-IP>
# Kafka CLI:    Bootstrap Server → <VM-IP>:29092

# 5. 로그 확인
docker compose logs -f kafka-test1
docker compose logs -f kafka-test2

# 6. 중지
docker compose down
```

### 유용한 명령어
```bash
# 특정 서비스만 재빌드
docker compose up --build kafka-test1

# 컨테이너 상태 확인
docker compose ps

# 볼륨까지 삭제 (DB 초기화)
docker compose down -v
```
