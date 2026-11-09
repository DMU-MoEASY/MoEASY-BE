# 모임 일정 API (#27)

모든 경로의 접두사는 `/api/v1`이다. 기존 JWT 쿠키 인증과 `ApiResponse<T>`를 사용하며,
쓰기 요청은 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더가 필요하다.

## Swagger 명세

`/v3/api-docs`와 Swagger UI에서 일정 전용 스키마를 사용한다.
요청은 `ScheduleCreate`·`ScheduleUpdate`, 응답은 `ScheduleCreated`·`ScheduleSummary`·`ScheduleDetail`·`SchedulePage`로 구분한다.
PATCH 본문은 `fields`로 감싸지 않고 변경 필드를 최상위에 전달한다. 선택 필드 쌍을 null로 제거하는 예시도 제공한다.
생성·수정·취소·삭제에는 필수 `X-XSRF-TOKEN` 헤더 입력을 제공한다.
`GET /api/v1/auth/csrf` 응답의 `result.token`을 입력하고 발급된 쿠키도 함께 전송한다.
생성 성공은 201, 나머지 성공은 200이며 API별 주요 오류 상태를 함께 문서화한다.

## API와 권한

| 메서드 | 경로 | 권한 | 성공 응답 result |
|---|---|---|---|
| GET | `/groups/{groupId}/schedules` | 활성 구성원 | Page (200) |
| POST | `/groups/{groupId}/schedules` | OWNER, MANAGER | `{scheduleId}` (201) |
| GET | `/schedules/{scheduleId}` | 활성 구성원 | Detail (200) |
| PATCH | `/schedules/{scheduleId}` | OWNER, MANAGER | 변경 후 Detail (200) |
| POST | `/schedules/{scheduleId}/cancel` | OWNER, MANAGER | null (200) |
| DELETE | `/schedules/{scheduleId}` | OWNER | null (200) |

멤버십은 ACTIVE·미삭제이며 회원도 미삭제여야 한다. 단건은 실제 소속 모임을 기준으로 검증한다.
ARCHIVED 모임은 읽기만 허용한다. CLOSED·삭제된 모임 및 삭제된 일정은 404를 반환한다.

## 생성

```json
{
  "title": "정기 모임",
  "description": "이번 주 모임 설명",
  "startsAt": "2026-12-10T18:00:00",
  "endsAt": "2026-12-10T20:00:00",
  "locationName": "모임 장소",
  "locationAddress": "서울특별시 강남구",
  "latitude": 37.5,
  "longitude": 127.0,
  "placeProvider": "KAKAO",
  "placeId": "12345"
}
```

시간은 Asia/Seoul의 `yyyy-MM-dd'T'HH:mm:ss`이며 오프셋·소수 초는 받지 않는다.
연도는 MySQL DATETIME 범위인 1000~9999다. 시작은 현재보다 미래, 종료는 시작보다 미래여야 한다.
제목·설명·장소명·주소는 필수이며 공백 불가, 각각 최대 100·500·100·255자다.
위도(-90~90)·경도(-180~180)는 소수 7자리까지이며 함께 입력하거나 함께 비운다.
placeProvider(KAKAO/GOOGLE)·placeId(공백 불가, 최대 100자)도 쌍으로 입력한다.
생성·수정 모두 장소 제공자는 문자열 `"KAKAO"`, `"GOOGLE"`만 허용하며 숫자·숫자 문자열은 400이다.

## 부분 수정

생성과 같은 필드를 받는다. 생략은 유지, 선택 필드 쌍의 명시적 null은 제거다.
필수 필드 null·공백, 빈 객체, 알 수 없는 필드(groupId/status/deletedAt 포함)는 400이다.
좌표와 장소 식별자는 각각 쌍으로 변경해야 한다. 시간은 기존 값과 합친 결과로 검증한다.
정상이며 시작 전인 일정만 수정할 수 있다. 시작 시각과 현재가 같으면 수정·취소 불가다.

## 목록

`from`, `to`를 함께 전달하거나 생략한다. 생략하면 한국 시간 이번 달이다.
범위는 `[from,to)`이며 `startsAt < to AND endsAt > from`인 일정을 반환한다.
`from`, `to`는 한국 시간 `yyyy-MM-dd'T'HH:mm:ss` 형식만 허용한다.
초 생략·오프셋·소수 초·유효하지 않은 날짜와 DB 범위 밖 연도(1000~9999 이외)는 400이다.
`from >= to`·한쪽만 전달·잘못된 날짜는 400이다. 취소·진행 중·과거 일정도 포함한다.
정렬은 `createdAt DESC, id DESC`. size 기본 20, 범위 1~100이다.

```text
Page: from, to, items[], nextCursor, hasNext
Summary: scheduleId, groupId, title, startsAt, endsAt, locationName, status
```

cursor는 서버가 발급한 문자열을 그대로 전달한다. 모임이나 기간이 다르거나 커서 날짜가 DB 저장 범위를 벗어나면 400이다.
후속 페이지에서 기간을 생략하면 커서의 최초 기간을 사용한다. 월이 바뀌어도 유지한다.
빈 목록은 items: [], nextCursor: null, hasNext: false이며 전체 개수는 제공하지 않는다.
정렬 키는 변경하지 않으므로 후속 페이지에 새 등록 일정이 끼어들지 않는다.
페이지 간 수정·취소·삭제까지 고정하는 스냅샷 조회는 제공하지 않는다.

## 상세

```text
scheduleId, groupId, title, description, startsAt, endsAt,
locationName, locationAddress, latitude, longitude, placeProvider, placeId,
status, createdAt, updatedAt
```

선택 정보가 없으면 null이다. status는 SCHEDULED/CANCELLED이며 진행·종료 여부는 시간으로 판단한다.
모든 응답 시간은 초 단위다. 내부 createdAt 커서는 DB의 마이크로초 정밀도를 보존한다.

## 취소·삭제와 정산

취소는 시작 전 SCHEDULED를 CANCELLED로 변경한다. 재취소·취소된 일정 수정은 409다.
삭제는 deleted_at 기록만 수행하고 OWNER는 진행 중·종료·취소 일정도 삭제할 수 있다.
재삭제는 404다. 정산·RSVP·출석·게시글·후기와 FK를 삭제하지 않는다.
취소·삭제에 따른 알림·정산 취소·환불은 수행하지 않는다.
정산은 `Schedule` 지연 로딩 연관관계로 연결하며 요청·응답의 `scheduleId`는 유지한다.
신규 정산은 같은 모임의 미삭제 일정을 조회해 연결한다. 취소된 일정도 연결할 수 있다.
기존 정산 상세·목록·일정 필터 조회에는 일정의 미삭제 조건을 적용하지 않으며 일정 없는 정산도 유지한다.

## 오류

| HTTP | 상황 / 코드 |
|---|---|
| 400 | 입력·기간·커서 오류: COMMON400_1 또는 VALID400_1 |
| 401 | 기존 인증 오류 계약 |
| 403 | 멤버십·역할·CSRF: 기존 권한 오류 계약 |
| 404 | 일정 없음·삭제: SCHEDULE404_1, 모임 없음·폐쇄·삭제: SCHEDULE404_2 |
| 409 | 시작·취소 상태: SCHEDULE409_1, 보관 모임 변경: SCHEDULE409_2 |

## 동시성 및 마이그레이션

쓰기 요청은 모임 → 일정 순서로 비관적 쓰기 잠금을 획득한다.
단건 변경은 모임 ID만 먼저 조회하고, 모임 잠금 후 일정 상태를 잠금 조회로 다시 읽는다.
기존 모임 폐쇄와 같은 잠금 순서로 수정·취소·삭제 상태 덮어쓰기를 방지한다.
상태·허용값 CHECK 및 목록 인덱스를 새 Flyway 마이그레이션으로 추가한다.
기존 일정은 DEFAULT SCHEDULED로 이행하며 적용된 이전 마이그레이션은 수정하지 않는다.

## 프론트엔드 연동

- 기존 이름·장소·날짜·시작 시간 외에 설명·종료 시간·주소 입력을 추가한다.
- 서버가 설명이나 종료 시간을 자동 생성하지 않는다.
- 한국 시간 문자열로 보내며 브라우저의 UTC 변환 문자열을 그대로 보내지 않는다.
- PATCH는 바뀐 필드만 보내고 좌표·장소 식별자는 쌍으로 보낸다.
- 취소 일정에는 취소 표시를 하고, 수정·취소 버튼은 시작 후 비활성화한다.
- 화면의 월 전체나 개수를 표시하려면 hasNext=false까지 조회한다.
- 참석 여부·인원은 RSVP 구현 전까지 표시용 임의 값으로 채우지 않는다.

## API별 코드 읽기 순서

공통으로 ScheduleController → 해당 Service → ScheduleAccess/입력 검증 → Repository/Entity → ScheduleConverter 순서로 읽는다.

1. 생성: createSchedule → ScheduleInput.validate → ScheduleConverter.toEntity → ScheduleRepository.save.
2. 상세: getSchedule → ScheduleAccess.require → ScheduleConverter.toDetail.
3. 목록: getSchedules → ScheduleCursor.decode → ScheduleRepository.findWindow → ScheduleConverter.toPage.
4. 수정: updateSchedule → lock → requireEditable → ScheduleInput.merge/validate → Schedule.update → toDetail.
5. 취소: cancelSchedule → lock → requireEditable → Schedule.cancel.
6. 삭제: deleteSchedule → lock(ownerOnly) → Schedule.softDelete.

ScheduleApiIntegrationTest는 HTTP·권한·경계·정산 보존을,
ScheduleConcurrencyIntegrationTest는 실제 별도 트랜잭션의 경합을,
ScheduleMigrationIntegrationTest는 MySQL 기존 행 상태 이행과 CHECK 제약을 검증한다.
