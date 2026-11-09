# Bank API 로컬 시뮬레이션

실제 Bank API 대신 로컬 HTTP 서버를 사용해 MoEASY의 전체 계좌 API를 검증한다.
공식 계약은 https://bankapi.co.kr/docs 에서 2026-11-10 확인했다.
Postman 로그인 세션이 없어 소유자의 공유 mock 검색은 실행하지 않았고 공식 문서를 근거로 로컬 mock을 작성했다.
외부에 배포하거나 실제 계좌의 키를 사용하지 않는다.

## 재현

1. 로컬 MySQL/Redis를 실행한다 (`task docker-up`).
2. 아래 서버를 실행한다.

```sh
postman simulation run postman/simulations/bankapi/bankapi.sim.yaml \
  --port 14010 --output ndjson --no-history
```

3. 다른 터미널에서 실제 Spring Controller·Service·HTTP 클라이언트를 검증한다.

```sh
BANK_SIMULATION_URL=http://localhost:14010/bankapi \
  ./gradlew test --tests '*BankApiIntegrationTest' --rerun-tasks
```

DB 포트가 다르면 기존 `DB_URL` 환경변수로 덮어쓴다. 테스트 전용 데이터만 사용한다.
로그에는 `/v1/transactions`와 `/v1/transactions/login`의 request 이벤트가 있어야 한다.
실제 개인 정보는 입력하지 않는다. Ctrl+C로 서버를 종료한다.

## 시나리오와 증거

- 가짜 비밀번호 1111: 정상 거래 2건(동일 금융 행 2개), 음수 잔액과 슬래시 날짜.
- 2222: HTTP 429, ACCOUNT_COOLDOWN, retryAfterSec=540.
- 3333: HTTP 200이지만 success=false, BANK_REJECTED.
- 4444: 소수 금액의 비정상 응답.
- 5555: HTTP 500.
- SH: 계좌별 results 구조를 반환한다.

2026-11-10 최종 실행: BankApiIntegrationTest 11개 중 10개가 통과하고 제어 가능한 peer가 필요한 동시성 테스트 1개를 생략했다. 모의 서버 로그에서 실제 HTTP 요청 11건을 확인했다.
성공·재조회 중복 방지·은행 실패 후 기존 거래/성공 시각 보존·일반 회원 조회·운영진 쓰기 제한을 확인했다.
동시 동기화 테스트는 제어 가능한 JDK HTTP peer를 사용하는 기본 통합 실행에서 검증하며 외부 시뮬레이션 실행에서는 생략한다.

## 한계

이 mock은 공급자의 실제 계좌 인증·국내 IP 검사·실시간 쿼터를 재현하거나 증명하지 않는다.
실제 키/계좌 없이 구현 계약과 실패 처리를 검증하기 위한 자료다.
공급자가 고유 거래 ID를 제공하지 않아 금융 필드와 동일 행 발생 순번으로 식별하는 것은 MoEASY의 정책이다.
공급자의 누락·사후 정정 응답까지 완전한 식별을 보장하는 것은 아니다.
