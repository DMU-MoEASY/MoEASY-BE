# 가용시간 투표 API와 구현 검토 안내 (#29)

## 검토 결과와 보완한 정책

이슈의 확정 정책을 구현 기준으로 사용한다. 기존 JWT 쿠키·CSRF·숫자 ID·ApiResponse와
Asia/Seoul의 `yyyy-MM-dd'T'HH:mm:ss` 규약을 유지한다. UUID·UTC·Bearer 초안은 적용하지 않는다.
Notion 기능/API 링크는 연결된 계정에서 404여서 내용을 대조하지 못했다.

구현 전에 사용자와 다음 두 경계를 확정했다.

- 확정 초기화 시 원래 시작 시간이 현재 이하이면 PATCH 전체를 409로 거절한다. 현재 확정과 선택은 유지한다.
- 탈퇴 후 재가입한 구성원은 미응답으로 시작한다. 재가입 전 선택은 집계·추천·본인 조회에서 제외하고 다시 제출해야 한다.

기술적으로 보완한 항목:

- 기존 ScheduleAccess의 쓰기 검사는 운영진 전용이다. 개인 응답은 별도 구성원 검사로 처리하되 같은 모임 → 일정 잠금 순서를 지킨다.
- 일정의 time_revision은 시간 변경 때만 증가한다. A → B → A처럼 시각이 돌아온 수정도 복원 충돌로 잡는다.
- 응답 완료 테이블을 별도로 두어 빈 응답과 미응답을 구분한다. 제출 당시 joined_at으로 재가입 이전 응답을 제외한다.
- 기존 WebSocket 구성은 없으므로 읽기 전용 원시 WebSocket을 추가한다. STOMP를 사용하지 않는다.
- 자동 마감은 1초 간격, 회당 최대 100개를 처리한다. 상태 반영 목표는 정상 부하에서 수 초이며, 적체·장애 중 지연 상한을 보장하지 않는다.
  응답 쓰기와 조회 상태 판단은 작업 지연과 무관하게 정확한 마감 시각을 적용한다.

## 구현 순서

1. 기존 일정·모임 권한·Flyway와 컨벤션을 확인하고 요청/응답 계약을 정한다.
2. 후보 설정·확정 정보·일정 시간 버전과 응답 완료 테이블을 추가한다.
3. 투표 생성·설정 조회와 개인 응답 전체 교체·조회·철회를 구현한다.
4. 일괄 조회한 슬롯·선택·현재 구성원으로 히트맵과 응답 현황을 조립한다.
5. 순수 추천 계산으로 실제 일정 길이를 수용하는 동일 구성원의 교집합을 구한다.
6. 기간 수정의 선택 보존, 확정 복원, 자동 마감·재개방을 연결한다.
7. 최신 스냅샷 토큰 검증과 일정 확정을 하나의 트랜잭션으로 처리한다.
8. 커밋 후 Redis 변경 알림과 WebSocket 권한 검사를 연결한다.
9. MySQL 동시성·실제 WebSocket·Redis 전달을 검증하고 전체 task check를 실행한다.

## REST 계약

접두사: `/api/v1/schedules/{scheduleId}`. 모든 성공 응답은 공통 ApiResponse의 result에 아래 값을 담는다.
쓰기 요청은 JWT 쿠키와 `XSRF-TOKEN` 쿠키에 대응하는 `X-XSRF-TOKEN` 헤더가 필요하다.
클라이언트가 다른 구성원 ID나 status를 지정하는 필드는 없다.

| 메서드 | 경로 | 권한 | 성공 result |
|---|---|---|---|
| POST | /availability-poll | OWNER·MANAGER | 201 Poll |
| GET | /availability-poll | 활성 구성원 | 200 Poll |
| PATCH | /availability-poll | OWNER·MANAGER | 200 Poll |
| DELETE | /availability-poll | OWNER | 200 null |
| PUT | /availability/me | 활성 구성원 본인 | 200 null |
| GET | /availability/me | 활성 구성원 본인 | 200 Me |
| DELETE | /availability/me | 활성 구성원 본인 | 200 null |
| GET | /availability | 활성 구성원 | 200 Detail |
| POST | /availability-poll/confirm | OWNER·MANAGER | 200 Poll |

POST/PATCH 후보 설정:

```json
{
  "startDate": "2099-01-10",
  "dayCount": 7,
  "startHour": 9,
  "endHour": 24,
  "voteDeadlineAt": "2099-01-09T23:00:00"
}
```

PATCH는 후보 설정 5개 필드를 모두 받는다. 부분 변경으로 오해하지 않도록 화면에서 현재 설정과 합쳐 보낸다.
`dayCount`는 3·5·7·14, 시간은 JSON 정수이며 시작 0~23·종료 1~24이고 종료가 더 늦어야 한다.
후보 일수·시간의 소수, 숫자 문자열, 불리언 입력은 생성과 설정 변경 모두 400으로 거부한다.
`24`는 해당 날짜 다음 날 00:00의 배타적 경계이다. 마감은 후보 범위와 별개의 미래 시각이다.
후보에는 과거 슬롯도 있을 수 있으나 과거/현재 시작은 추천·확정하지 않는다.

PUT 개인 선택:

```json
{"slots":["2099-01-10T09:00:00","2099-01-10T09:30:00"]}
```

빈 배열은 응답 완료, DELETE는 미응답이다. 같은 PUT의 재전송은 중복 선택이나 버전 증가를 만들지 않는다.
후보 밖·중복·30분 미정렬·밀리초/시간대 접미사가 있는 슬롯은 400이다. 슬롯은 최대 672개다.
선택이 없는 본인의 DELETE도 OPEN에서는 성공한다. 마감 후·확정 후에는 철회도 409다.

확정:

```json
{"startsAt":"2099-01-10T09:00:00","token":"통합 조회에서 받은 64자리 토큰"}
```

토큰은 투표 revision·유효 상태·일정 시간 버전/시각/상태·모임 상태·활성 명단과 가입 시점/수정 정보를 반영한다.
가능 인원과 종료 시각은 서버가 재계산한다. 임의 시작이나 오래된 결과는 409다.
같은 시작과 같은 원본 토큰의 재전송은 일정이 여전히 변경 가능한 상태이고 확정 이후 시간이 변경되지 않았다면 성공한다.
다른 시간의 재확정은 PATCH 재개방이 필요하다. CLOSED에서도 최신 토큰으로 확정할 수 있다.

Poll은 설정·상태·slotMinutes·token·confirmed·확정 시작/종료를 포함한다.
Me는 responded·정렬된 slots·editable을 포함한다.
Detail은 poll, targetCount, respondedCount, pendingCount, slots, me, recommendations를 포함한다.
각 slots 원소는 startsAt·availableCount·selected이다. 0명 슬롯도 반환한다.
추천은 startsAt·endsAt·availableCount·targetCount·ratio(0~1)를 반환한다.
추천 개수는 제한하지 않고 인원 내림차순/시각 오름차순으로 모두 반환한다.
일정 길이가 45분이면 실제 종료는 45분 후이며 걸치는 두 30분 슬롯의 동일 구성원 교집합을 계산한다.
날짜별 후보 범위를 넘는 구간은 제외한다.

기간 수정은 남은 슬롯·선택과 빈 응답 완료 기록을 보존한다. 확정되었다면 원래 일정 시간을 복원한다.
확정 이후 일반 일정 시간 수정, 과거가 된 원래 시간은 복원 충돌이다. 제목·설명·장소 수정은 복원을 방해하지 않는다.
투표 삭제는 자식부터 물리 삭제하고 일정·RSVP·정산을 보존한다. 확정된 일정 시간도 유지한다.

오류: 입력 400, 인증 401, 권한/CSRF 403, 대상 404, 중복·상태·오래된 토큰·복원 충돌 409.
보관 모임은 읽기만 허용하고 폐쇄·삭제 모임/삭제 일정은 접근할 수 없다.
취소/시작 일정은 개인 응답·설정 변경·확정이 불가하다. OWNER 투표 삭제는 기존 일정 삭제와 같이 허용한다.

## 잠금과 일관성

모든 변경은 모임 → 일정 순서로 비관적 잠금을 잡고 최신 자격과 상태를 재검증한다.
투표 변경은 해당 일정 잠금으로 직렬화한다. UNIQUE는 중복 생성과 응답의 최종 방어선이다.
변경 트랜잭션은 READ_COMMITTED로 잠금 전 읽기의 오래된 스냅샷을 재사용하지 않는다.
조회는 REPEATABLE_READ로 한 응답 안의 명단·선택·현황·토큰을 같은 DB 스냅샷에서 구성한다.

가입 승인과 모임 폐쇄, 일반 일정 수정/취소/삭제도 커밋 후 변경 알림을 발행한다.
현재 저장소에는 모임 탈퇴·강퇴·회원 삭제 API가 없다. 해당 기능을 추가할 때는 같은 잠금 규약과
AvailabilityChanged(groupId, null, "ROSTER_CHANGED") 이벤트를 각 영향 모임에 연결해야 한다.
현재 구현은 DB에 자격 변경이 반영되면 REST 집계에서 즉시 제외하고, 매 알림 전 재검사와 주기 검사로 수신을 차단한다.
직접 DB 수정은 변경 이벤트를 만들지 않으므로 프론트의 주기 재조회가 필요하다.

## WebSocket과 프론트엔드 연동

연결: `/ws/schedules/{scheduleId}/availability` (HTTPS 배포에서는 wss).
구독은 URL의 일정 하나로 고정한다. 별도 SUBSCRIBE 메시지나 STOMP destination은 없다.
연결에는 유효한 JWT 쿠키·설정된 CORS Origin·활성 일정 구성원 자격이 필요하다.
Origin 없는 연결과 허용되지 않은 Origin도 거절한다. 소켓은 읽기 전용이며 텍스트 명령을 보내면 종료한다.
Swagger의 6개 쓰기 API는 `X-XSRF-TOKEN` 헤더 입력을 제공한다. `GET /api/v1/auth/csrf`로
토큰과 `XSRF-TOKEN` 쿠키를 발급받고 응답의 `result.token`을 헤더로 전달한다.
HTTP 쓰기의 기존 CSRF 검증은 유지한다. 소켓에서는 상태 변경을 허용하지 않는다.
JWT 만료·구성원 자격·모임/일정 접근은 알림 직전과 1초 간격으로 재검증한다.
각 검사와 알림마다 일정·회원 조합을 중복 제거하고 최대 128개씩 조인 조회한다.
계정·구성원·모임·일정의 상태와 삭제 여부를 같은 쿼리에서 확인하며 권한 결과를 다음 검사에 캐시하지 않는다.
JWT 만료는 DB 조회 전후와 각 연결의 전송 직전에 확인한다. 권한 조회 실패 시 대상 연결을 닫는다.
소켓 점검과 자동 마감은 별도 단일 스레드 스케줄러를 사용하며 스케줄러 스레드를 공유하지 않는다.
권한 상실은 정책 위반 코드 1008로 닫는다. 이미 네트워크로 전송한 프레임의 회수는 보장하지 않는다.

```json
{"type":"RESPONSE_CHANGED","scheduleId":123}
```

개인 응답·선택·구성원 ID는 알림에 넣지 않는다. type은 CONNECTED, CREATED, RESPONSE_CHANGED,
RESPONSE_WITHDRAWN, REOPENED, CLOSED, CONFIRMED, DELETED, SCHEDULE_CHANGED,
SCHEDULE_CANCELLED, SCHEDULE_DELETED, ROSTER_CHANGED, GROUP_CLOSED다.
어떤 변경이든 REST 통합 조회를 다시 수행한다. 삭제/자격 상실은 404/403 또는 소켓 종료로 처리한다.

각 인스턴스는 Redis `moeasy:availability:changed` 채널을 구독한다. 트랜잭션 AFTER_COMMIT에만 publish한다.
Pub/Sub은 영속 큐가 아니므로 장애/재연결 구간의 유실과 중복 가능성을 전제로 한다.
Redis 발행 실패가 이미 커밋된 REST 변경을 실패처럼 보이게 하지 않도록 로그만 남긴다.
DB 커밋 직후 프로세스가 종료되면 알림이 누락될 수 있다. REST가 최종 상태의 기준이다.

프론트엔드 구현 기준:

1. 첫 화면·CONNECTED·재연결·탭 복귀에 즉시 통합 조회한다.
2. 연결 중에도 60초마다 통합 조회한다. 숨겨진 탭은 복귀 즉시 조회한다.
3. 알림은 150ms 단위로 병합한다. 조회 중 알림이 오면 완료 직후 한 번 더 조회한다.
4. 요청마다 로컬 순번을 증가시키고 최신 요청의 응답만 적용해 응답 역전을 방지한다.
5. 실패 시 1·2·4·8·최대 30초 지수 지연에 지터를 더해 재시도한다. 성공하면 지연을 초기화한다.
6. 401은 인증 갱신 후 재연결, 403/404는 접근 종료, 확정 409는 재조회 후 사용자가 다시 선택한다.

이 저장소에는 해당 프론트엔드 코드가 없으므로 위 병합·재시도·탭 동작의 브라우저 검증은 별도 연동 범위다.
백엔드 검증은 실제 WebSocket, 커밋/롤백 알림, 수신 차단, 동일 Redis의 두 수신 노드를 다룬다.
운영 프록시의 Upgrade 전달·유휴 제한과 실제 두 애플리케이션 배포 검증은 운영 환경에서 확인해야 한다.

## 마이그레이션·운영

`V20261104224032__expand_availability_poll.sql`은 기존 세 availability 테이블을 재사용하고
availability_response와 schedule.time_revision을 추가한다. 기존 적용 파일은 변경하지 않았다.
`V20261104225745__restrict_availability_response_updates.sql`은 신규 응답 FK의 ON UPDATE를
프로젝트의 명시적 RESTRICT 규칙에 맞추는 후속 보정이다.
구현 전 task db-info와 로컬 테이블을 확인했으며 로컬 투표·선택은 0건이었다.

다른 환경의 구 데이터에는 일별 설정이나 확정 전 원래 시간이 없으므로 자동 추정해서 이행하지 않는다.
마이그레이션 첫 단계는 availability_poll이 비어 있지 않으면 DDL 전에 CHECK 오류로 중단한다.
배포 전에 해당 환경에서 투표 수를 확인해야 한다. 기존 행이 있다면 원본 백업과 의미 확인 후 별도 이행을 설계한다.
빈 테이블 전제가 충족되지 않는 환경에서 임의 삭제나 Flyway repair로 우회하지 않는다.

설정(기본값): availability.close-delay-ms=1000, availability.socket-check-ms=1000.
테스트 프로파일에서는 백그라운드 Clock mock 경합을 피하려고 지연을 1시간으로 두고 작업을 직접 호출한다.
서비스 재시작 시 별도 메모리 타이머 복구 없이 DB의 마감된 OPEN 목록을 다시 처리한다.
마감 작업은 최신 마감을 잠금 후 다시 검사하므로 이전 마감이 재개방된 투표를 닫지 않는다.

## API별 코드 읽기 순서

모든 경로는 `src/main/java/com/moeasy/moeasybe/domain/schedule/` 기준이다.
먼저 `dto/request/AvailabilityReqDTO`, `dto/response/AvailabilityResDTO`,
`controller/docs/AvailabilityControllerDocs`로 계약을 확인한다.

| 순서 | API | Controller → Service → 핵심 코드 |
|---|---|---|
| 1 | POST /availability-poll | createPoll → AvailabilityCommandService.create → AvailabilitySupport.access → AvailabilityInput.validate/slots → AvailabilityConverter.toEntity → Poll/SlotRepository |
| 2 | GET /availability-poll | getPoll → AvailabilityQueryService.getPoll → AvailabilitySupport.snapshot → AvailabilityConverter.toPoll |
| 3 | PUT /availability/me | submit → AvailabilityCommandService.submit → requireOpen → 후보 전체 검증 → Response/VoteRepository → poll.touch |
| 4 | GET /availability/me | getMe → AvailabilityQueryService.getMe → snapshot의 재가입·활성 필터 → AvailabilityConverter.toMe |
| 5 | DELETE /availability/me | withdraw → AvailabilityCommandService.withdraw → 선택 삭제 → 완료 기록 삭제 |
| 6 | GET /availability | getAvailability → AvailabilityQueryService.getAvailability → snapshot → AvailabilityRecommendation.calculate → AvailabilityConverter.toDetail |
| 7 | POST /availability-poll/confirm | confirm → AvailabilityCommandService.confirm → 토큰·추천 재검증 → Schedule.reschedule → AvailabilityPoll.confirm |
| 8 | PATCH /availability-poll | configurePoll → AvailabilityCommandService.configure → 복원 충돌 검사 → Schedule.reschedule → AvailabilityPoll.configure → 제외 선택/슬롯 삭제·새 슬롯 추가 |
| 9 | DELETE /availability-poll | deletePoll → AvailabilityCommandService.delete → Vote → Response → Slot → Poll 삭제 |

공통 실시간 흐름은 AvailabilityChanged → AvailabilityEventRelay → Redis → AvailabilitySocketHandler 순서다.
권한·연결은 AvailabilityRealtimeConfig/AvailabilitySubscriptionAccess, 마감은 AvailabilityDeadlineJob/closeDue를 읽는다.

검증 코드는 AvailabilityApiIntegrationTest → AvailabilityRecommendationTest →
AvailabilityConcurrencyIntegrationTest → AvailabilityRealtimeIntegrationTest 순서로 읽는다.
동시성 테스트는 별도 DB 트랜잭션을 실제로 경합시킨다. 두 Redis 수신 노드 테스트는
다중 인스턴스의 전달 구조를 검증하며 운영 배포 자체를 대신하지 않는다.
