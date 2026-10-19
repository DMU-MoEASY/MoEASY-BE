# 회비·정산 API 명세

## 범위와 구현 상태

이 문서는 정산 API의 개발 기준과 연동 계약을 정의한다. 계약이 정의된 기능과 실제 구현된 기능은 아래 표로 구분한다. API·스키마를 변경할 때 계약과 구현 상태를 함께 갱신한다.

| 기능 | 구현 상태 | 계약 |
| --- | --- | --- |
| JWT 쿠키 인증·CSRF 검증 | 구현됨 | 기존 공통 시큐리티 설정 사용 |
| 정산·회원별 청구·납부·영수증 테이블 | 초기 스키마 구현됨 | [현재 데이터 구조](#현재-데이터-구조) 참고 |
| 정산·회원별 청구 엔티티와 저장 제약 | 구현됨 | 총액·분할 방식·상태·연관 관계 매핑. 금액 범위·대상 중복 DB 제약 |
| 정산 생성 DTO·성공/오류 코드 | 구현됨 | 필드 검증·JSON 정수/날짜 변환·생성 결과 |
| 금액 분할 계산 | 구현됨 | 균등 분할·회원별 지정 금액 검증·중복 대상 거부·오버플로 감지. DB 조회·저장 없음 |
| 정산 생성 Service·Repository·Converter | 구현됨 | 권한·일정·대상·조건부 필드·납부 기한 검증 및 정산·청구의 트랜잭션 저장 |
| 정산 생성 엔드포인트·Swagger | 구현됨 | JWT·CSRF 검증, 요청 validation, HTTP 201 공통 응답과 요청·응답 예시 |
| 대상 선택용 모임 회원 목록 조회 | 미구현 | 경로·필터·응답 필드 정의됨 |
| 분할 미리보기 | 구현됨 | JWT·CSRF 검증, 권한·대상·금액 검증, HTTP 200 회원별 분할 결과와 Swagger 예시. 데이터 미저장 |
| 정산 목록·상세 조회 DTO·성공/오류 코드 | 구현됨 | 목록 조건·페이지 기본값과 범위 검증, 목록·상세 응답 데이터 형태, 조회 성공·정산 없음 코드 |
| 정산 목록 조회 Service·Repository·Converter | 구현됨 | 활성 회원 권한, 폐쇄 모임 허용, 필터·정렬·페이지 집계와 저장된 청구 인원수 일괄 조회 |
| 정산 목록 조회 엔드포인트·Swagger | 구현됨 | JWT 쿠키 인증, 경로·쿼리 입력 검증, HTTP 200 공통 페이지 응답과 Swagger 예시 |
| 정산 상세 조회 | 미구현 | [정산 목록·상세 조회](#정산-목록상세-조회)의 경로·권한·응답 정의됨 |
| 납부·환불·영수증 관리·수정·취소·마감 | 미구현 | [후속 기능](#후속-기능) 참고 |

정산 생성·분할 미리보기·조회는 [Issue #20](https://github.com/DMU-MoEASY/MoEASY-BE/issues/20)의 범위다. 모임 회원 목록 조회는 프론트의 대상 선택 연동에 필요한 별도 API이며 `group` 도메인에서 제공한다. 정산 생성 API는 대상 ID로 직접 소속·상태를 검증하므로 회원 목록 조회 API의 구현에 의존하지 않는다. 미구현 API는 현재 호출할 수 없다.

## 공통 계약

- 모든 경로의 공통 접두사는 `/api/v1`이다.
- 정상·오류 응답은 기존 `ApiResponse<T>` 형식을 사용한다.
- 인증에는 JWT HttpOnly 쿠키를 사용한다. POST·PATCH·PUT·DELETE 요청에는 기존 CSRF 쿠키와 `X-XSRF-TOKEN` 헤더가 필요하다. 저장하지 않는 POST 미리보기도 같은 검증을 적용한다.
- 생성자·요청자의 회원 ID는 JWT에서 주입받으며 요청 본문으로 받지 않는다.
- `groupId`는 `member_group.id`다. 정산 대상의 `groupMemberId`는 `group_member.id`이며 로그인 회원의 `memberId`와 다르다. 모든 ID는 양의 정수다.
- 활성 모임 회원은 같은 모임에 속하며 `group_member.status=ACTIVE`, `group_member.deleted_at IS NULL`이고 연결된 `member`도 삭제되지 않은 회원이다.

## 정산 생성

`POST /api/v1/groups/{groupId}/settlements`

### 권한

- 존재하며 폐쇄되지 않은 모임에서 활성 `OWNER` 또는 `MANAGER`만 생성할 수 있다.
- 일반 회원, 탈퇴·강퇴 회원, 모임에 가입하지 않은 회원은 생성할 수 없다.

### 요청

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `title` | string | 필수. trim 후 공백이 아닌 1~100자 |
| `description` | string 또는 null | 선택. 최대 500자 |
| `dueAt` | string 또는 null | 선택. `yyyy-MM-dd'T'HH:mm:ss`, 한국 시간(`Asia/Seoul`) 기준. 값이 있으면 생성 시각 이후 |
| `scheduleId` | integer 또는 null | 선택. null/생략이면 모임 회비. 값이 있으면 같은 모임에 속하고 삭제되지 않은 일정 |
| `splitMode` | string | 필수. `EQUAL` 또는 `CUSTOM` |
| `totalAmount` | integer | 두 분할 방식 모두 필수. 원 단위 양수 |
| `groupMemberIds` | integer 배열 | `EQUAL`에서만 필수. 정산 대상 모임 회원 ID 목록 |
| `memberAmounts` | 객체 배열 | `CUSTOM`에서만 필수. 각 객체는 `groupMemberId`, 원 단위 양수 `amount` |

금액과 ID는 JSON 숫자로 전달한다. 숫자 문자열, 소수·지수 표기, Java `Long` 범위 초과는 거부한다. 프론트의 JavaScript `Number`는 9,007,199,254,740,991을 넘는 정수의 정확성을 보장하지 않는다. 현재 계약은 JSON 숫자를 유지하며, 이 범위 밖의 값을 프론트에서 계산·전송할 때는 정밀도 손실에 유의해야 한다. 서버의 저장 가능 범위와 프론트 `Number`의 정확한 처리 범위는 다르다.

필수 배열은 비어 있을 수 없다. 사용하지 않는 조건부 필드는 생략하거나 null로 전달한다. 해당 필드에 값이 들어오면 잘못된 조합으로 거부한다.

요청 DTO는 필수값·길이·양수·빈 배열·중첩 항목을 검증하고 제목의 앞뒤 공백을 제거한다. 분할 방식에 필요한 배열의 누락·불필요한 필드 전달, 중복 대상·금액·합계 검증과 배분 계산은 `SettlementSplitCalculator`에서 담당한다. 생성 권한, 일정과 대상의 실제 존재·소속·상태, 납부 기한은 `SettlementCommandService`에서 검증한다. `SettlementController`에서 요청 DTO를 검증하고 JWT principal의 회원 ID와 함께 서비스로 전달한다.

| 분할 방식 | 대상·금액 처리 |
| --- | --- |
| `EQUAL` | `groupMemberIds`를 대상으로 서버가 `totalAmount`를 분할한다. |
| `CUSTOM` | `memberAmounts`에 명시된 회원이 대상이다. 서버가 개별 금액을 합산하여 `totalAmount`와 정확히 일치하는지 검증한다. `groupMemberIds`는 중복 전달하지 않는다. |

모임 회비의 균등 분할 요청:

```json
{
  "title": "10월 모임 회비",
  "description": "모임 운영비입니다.",
  "dueAt": "2026-11-01T23:59:59",
  "splitMode": "EQUAL",
  "totalAmount": 10000,
  "groupMemberIds": [11, 12, 13]
}
```

일정에 연결된 균등 분할 요청:

```json
{
  "title": "러닝 후 식사비",
  "scheduleId": 7,
  "splitMode": "EQUAL",
  "totalAmount": 10000,
  "groupMemberIds": [11, 12, 13]
}
```

회원별 금액 지정 요청:

```json
{
  "title": "식사비 개별 정산",
  "scheduleId": 7,
  "splitMode": "CUSTOM",
  "totalAmount": 20000,
  "memberAmounts": [
    { "groupMemberId": 11, "amount": 12000 },
    { "groupMemberId": 12, "amount": 8000 }
  ]
}
```

마지막 예시의 개별 금액 합계는 20,000원이다. 요청 총액이 25,000원이면 자동 보정하지 않고 오류를 반환한다.

### 대상·금액 검증과 저장

- 대상은 요청에 명시된 모임 회원 ID로 확정한다. 이후 신규 가입한 회원을 자동 추가하지 않는다.
- 다른 모임 회원, 탈퇴·강퇴·삭제 회원, 존재하지 않는 회원이 포함되면 요청 전체를 거부한다. 잘못된 대상을 조용히 제외하지 않는다.
- 대상 ID가 중복되면 거부하며, 중복 제거 후 처리하지 않는다.
- `scheduleId`는 정산의 연결 정보다. 생성 API는 일정 참석 여부로 대상을 자동 제한하지 않는다.
- 모임장·운영진·생성자도 대상에 포함할 수 있다. 생성자라는 이유로 청구에서 제외하거나 납부 완료 처리하지 않는다.
- 금액은 원 단위 정수이며 소수·음수·0원은 허용하지 않는다. `CUSTOM`의 합계가 총액과 다르면 거부한다.
- 균등 분할 총액은 대상 인원수 이상이어야 한다. 이보다 작으면 0원 청구가 생기므로 거부한다.
- 별도의 업무상 금액 상한은 두지 않는다. 개별 금액과 총액은 Java `Long`과 DB 컬럼이 함께 허용하는 양수 범위로 제한한다. 현재 회원별 금액 컬럼은 `BIGINT UNSIGNED`이므로 Java 처리 범위인 1~9,223,372,036,854,775,807원을 사용한다. 새 총액 컬럼도 이 범위를 저장할 수 있어야 한다.
- `CUSTOM` 합계는 오버플로를 감지하며 계산한다. 입력값·합계의 범위 초과는 HTTP 400으로 거부하고, 숫자가 잘못된 값으로 순환하거나 DB 오류로 이어지지 않도록 저장 전에 검증한다. JSON 전송 과정의 큰 정수 정밀도는 [요청](#요청)에 명시한 프론트 `Number`의 제한에 유의한다.
- 검증이 성공하면 정산과 회원별 청구를 하나의 트랜잭션으로 저장한다. 실패 시 부분 저장하지 않는다.
- `settlement.status=OPEN`, `settlement_item.status=PENDING`으로 생성한다. 납부 기록은 생성하지 않는다.
- 확정된 대상·청구 금액은 이후 회원 가입·탈퇴로 자동 변경하지 않는다.

### 내부 생성 흐름

`SettlementCommandService.createSettlement`는 다음 흐름을 하나의 트랜잭션으로 실행한다.

1. 폐쇄·삭제되지 않은 모임과 요청자의 활성 모임 회원 정보를 조회하고 `OWNER`·`MANAGER` 권한을 확인한다. 모임 회원 정보와 연결된 회원 계정이 삭제된 경우에도 생성할 수 없다.
2. 일정이 지정되면 같은 모임에 속하고 삭제되지 않았는지 확인한다.
3. 분할 방식과 조건부 필드 조합을 검증한 뒤 `SettlementSplitCalculator`로 회원별 청구액을 확정한다.
4. 대상 ID들을 한 번에 조회하여 모두 같은 모임의 활성 회원인지 확인한다. 모임 회원 정보 또는 연결된 회원 계정이 삭제된 대상은 거부한다.
5. 주입받은 `Clock`의 현재 시각을 `Asia/Seoul`로 변환해 납부 기한을 검증한다. 서버의 기본 시간대와 무관하게 한국 시간으로 해석한다.
6. `SettlementConverter`로 정산·청구 엔티티를 조립해 저장하고 생성 식별자 DTO를 반환한다. 계산 결과의 `amount`에는 나머지 배분이 이미 포함되므로 청구 조립 시 다시 더하지 않는다.

모임·회원 조회는 기존 `group` 도메인의 Repository를 사용한다. 일정은 아직 JPA 엔티티가 없어 `schedule.repository.ScheduleRepository`에서 `JdbcTemplate`으로 존재·소속·삭제 여부만 조회한다. 정산 서비스에는 SQL이나 일정 객체 조립을 두지 않는다. 청구 일부 저장 후 실패한 경우까지 포함해 서비스 트랜잭션의 전체 롤백을 MySQL 통합 테스트로 검증한다.

### 생성 요청 재시도

- 생성 요청 단위의 중복 방지는 현재 구현 범위에 포함하지 않는다. 요청 식별 키나 미리보기 토큰을 이용한 멱등 처리는 제공하지 않는다.
- 같은 내용으로 생성 API를 다시 호출하면 별도의 정산이 생성될 수 있다. 요청 내부의 대상 ID 중복 검증은 생성 요청 자체의 중복 방지와 별개다.
- 프론트는 생성 요청 처리 중 제출 버튼을 비활성화하고 생성 요청을 자동 재시도하지 않는다. 이 처리는 재시도에 대한 서버 측 중복 방지를 보장하지 않는다.
- 타임아웃·연결 끊김 등으로 결과가 불확실하면 저장 실패로 단정하지 않는다. 정산 목록의 생성 내역을 참고하도록 안내하고, 확인 없이 즉시 다시 생성하지 않는다. 목록 조회가 생성 요청의 멱등성이나 동일 내용 요청의 성공 여부를 보장하는 것은 아니다. 상세 조회는 아직 구현되지 않았다.

### 성공 응답

HTTP 201과 생성한 `settlementId`를 `ApiResponse<SettlementResDTO.Created>` 형식으로 반환한다.

```json
{
  "isSuccess": true,
  "code": "SETTLEMENT201_1",
  "message": "정산 생성에 성공했습니다.",
  "result": {
    "settlementId": 1
  }
}
```

### Swagger에서 생성 테스트

1. API를 호출하는 호스트에 유효한 `moeasy_access_token` 쿠키가 있어야 한다.
2. `GET /api/v1/auth/csrf`를 호출해 CSRF 쿠키를 발급받고 응답의 `result.token` 값을 복사한다.
3. 정산 생성 API의 `groupId`와 요청 본문을 입력한다. 대상 ID는 `group_member.id`를 사용한다.
4. `X-XSRF-TOKEN` 헤더 입력란에 복사한 값을 넣어 실행한다. CSRF 쿠키도 같은 요청에 전달되어야 한다.
5. 성공하면 HTTP 201, `SETTLEMENT201_1`, `result.settlementId`를 확인한다.

`SettlementControllerDocs`는 공통 성공 응답 스키마, 균등·개별 금액 요청 예시와 400·401·403·404 실패 응답을 정의한다. 브라우저 프론트에서 호출할 때도 쿠키와 CSRF 헤더가 필요하며, 교차 출처 요청은 `credentials: 'include'` 또는 `withCredentials: true`를 설정한다.

## 대상 선택용 회원 목록 조회

| 요청 | 응답 대상 |
| --- | --- |
| `GET /api/v1/groups/{groupId}/members` | 전체 활성 모임 회원 |
| `GET /api/v1/groups/{groupId}/members?scheduleId=7` | 해당 일정에 참석 예정인 활성 모임 회원 |

- 요청자는 해당 모임의 활성 회원이어야 한다. 모임이 없거나 폐쇄·삭제된 경우 조회할 수 없다.
- `scheduleId`는 선택 필터이며, 같은 모임에 속하고 삭제되지 않은 일정인지 검증한다.
- 일정 참가자는 실제 출석 기록이 아닌 `schedule_rsvp.status=ATTENDING`인 참석 예정 회원을 의미한다.
- 회원 목록에는 조건에 맞는 모임장·운영진·요청자도 포함한다.
- `groupMemberId` 오름차순으로 전체 결과를 반환한다. 참가자가 없으면 빈 배열을 반환하며 전체 모임 회원으로 대체하지 않는다.
- HTTP 200과 공통 응답 형식을 사용한다. `result`는 다음과 같다. 성공·오류 코드의 세부 계약은 [미정 계약](#미정-계약)에 명시한다.

```json
{
  "members": [
    { "groupMemberId": 11, "memberId": 101, "nickname": "정굴", "role": "OWNER" },
    { "groupMemberId": 12, "memberId": 102, "nickname": "러너", "role": "MEMBER" }
  ]
}
```

프론트는 모임 회비에서 전체 회원, 일정 회비에서 일정 참가자 목록을 기본 선택에 사용한다. 명단별로 선택 상태를 나누지 않고 `groupMemberId` 기준의 공통 선택 목록을 유지한다. 명단을 전환해도 선택을 유지하며 현재 명단에 없는 선택 회원도 확인·해제할 수 있어야 한다. 전체 선택·해제는 현재 표시된 명단에 적용한다.

최종 생성 요청에는 선택한 대상만 전달한다. 목록 조회 후 대상이 탈퇴·삭제되면 생성 단계에서 거부하므로 목록을 갱신하고 대상을 다시 확인한다.

## 금액 분할과 미리보기

### 균등 분할 규칙

대상 `groupMemberId`를 오름차순 정렬한다. 각 회원에게 `총액 / 인원수`의 몫을 배분하고, 나머지 인원만큼 앞에서부터 1원씩 추가한다. 요청 배열 순서와 관계없이 같은 대상·총액이면 같은 결과를 반환한다.

| 총액 | 대상 ID | 최종 청구액 | 나머지 추가분 |
| --- | --- | --- | --- |
| 10,000원 | 11 / 12 / 13 | 3,334원 / 3,333원 / 3,333원 | 1원 / 0원 / 0원 |
| 500원 | 11 / 12 / 13 | 167원 / 167원 / 166원 | 1원 / 1원 / 0원 |

### 계산 구현

- 정산 전용 계산 로직은 `util/SettlementSplitCalculator.java`에 두고, 계산 결과는 그 클래스 내부의 `Allocation` record로 정의한다. 정산 처리 서비스는 `service.command`와 `service.query`로 구분한다. 생성·미리보기 서비스는 같은 계산기를 사용하며 목록 조회는 저장된 총액과 청구 인원수를 반환하고 금액을 재계산하지 않는다.
- 공통 진입점 `calculate`가 분할 방식과 조건부 배열 조합을 검증한 뒤 `calculateEqual` 또는 `calculateCustom`으로 위임한다. 사용하지 않는 배열에 빈 배열을 전달해도 잘못된 조합으로 거부한다.
- `SettlementSplitCalculator.calculateEqual`은 대상 ID를 오름차순 정렬한 후 위 규칙으로 배분한다. 총액이 인원수보다 작아 0원 청구가 생기면 거부한다.
- `calculateCustom`은 회원별 양수 금액을 보존하며 `Math.addExact`로 합산한다. `Long` 범위 초과 또는 요청 총액과의 불일치는 거부하고 자동 보정하지 않는다.
- 두 메서드는 중복 ID를 제거하지 않고 오류로 처리한다. DTO 검증을 거치지 않는 내부 호출에서도 빈 대상·null 항목·양수가 아닌 ID·금액을 거부한다.
- 반환값은 `groupMemberId`, 청구액 `amount`, 나머지 추가분 `remainderAmount`를 담은 내부 결과 목록이다. 두 방식 모두 ID 오름차순이며 `CUSTOM`의 나머지 추가분은 0원이다. 입력 목록의 순서는 변경하지 않는다.
- 계산기는 DB를 조회하거나 저장하지 않는다. 생성·미리보기 서비스가 재사용하며 요청자 권한과 회원의 실제 존재·소속·상태는 서비스에서 별도로 검증한다.
- 대상 조회·소속·활성·삭제 여부 검증은 `service.support.SettlementTargetFinder.findTargets`를 생성·미리보기 서비스에서 공유한다. 계산기가 검증한 배분 목록을 입력받아 대상 ID별 모임 회원을 반환하며, 트랜잭션 경계는 호출하는 서비스가 관리한다.

### 분할 미리보기 API

`POST /api/v1/groups/{groupId}/settlements/preview`

미리보기 API와 Swagger는 구현되어 있다. `SettlementController.previewSplit`이 요청 DTO를 검증하고 JWT 회원 ID와 함께 `SettlementQueryService.previewSplit`을 호출한다. POST 요청이지만 데이터를 변경하지 않는 조회 성격의 유스케이스이므로 QueryService에서 처리하고 생성과 같은 `SettlementSplitCalculator`를 재사용한다.

`previewSplit`은 `@Transactional(readOnly = true)` 안에서 다음 순서로 처리한다.

1. 폐쇄·삭제되지 않은 모임과 요청자의 활성 모임 회원 정보를 조회하고 `OWNER`·`MANAGER` 권한을 확인한다.
2. 공통 계산기로 조건부 필드·대상 ID·금액을 검증하고 ID 오름차순의 분할 결과를 계산한다.
3. 대상 목록을 한 번에 조회하여 같은 모임의 활성·미삭제 회원인지 확인한다. 회원 계정도 함께 로딩하여 닉네임별 추가 조회를 피한다.
4. `SettlementConverter.toPreview`가 계산 결과와 현재 닉네임을 조립한다. 정산·청구 Repository를 통한 저장은 수행하지 않는다.

실제 MySQL 통합 테스트에서 서비스 트랜잭션 종료 후 정산·청구 미저장, 권한·대상 검증과 동일 입력의 생성·미리보기 청구액 일치를 확인한다.

HTTP 통합 테스트에서는 실제 JWT 필터와 CSRF 발급 API를 사용해 인증·CSRF 실패, 모임·권한·분할 규칙 오류, 정상 응답과 정산·청구 미저장, OpenAPI 스키마·예시를 검증한다.

#### 권한과 검증

- 폐쇄·삭제되지 않은 모임의 활성 `OWNER`·`MANAGER`만 요청할 수 있다. 생성과 같은 인증·권한·대상 기준을 사용한다.
- JWT 인증 쿠키와 CSRF 쿠키·`X-XSRF-TOKEN` 헤더가 필요하다. POST이므로 저장 여부와 관계없이 기존 CSRF 검증을 적용한다.
- 분할 방식·조건부 필드·대상 중복·회원 소속 및 활성 상태·금액 범위·CUSTOM 합계를 생성과 동일하게 검증한다.
- 정산·청구 데이터를 저장하거나 예약하지 않는다. 미리보기 식별자나 토큰을 발급하지 않는다.
- 제목·설명·일정·납부 기한은 요청 필드에 포함하지 않는다. 선택한 대상 ID만으로 계산하며, 일정의 유효성 등 생성에 필요한 나머지 정보는 생성 요청에서 검증한다.

#### 요청

`SettlementReqDTO.Preview`는 생성 요청 중 `splitMode`, `totalAmount`, `groupMemberIds`, `memberAmounts`만 받는다. JSON 정수 형식·양수 범위·배열·중첩 항목 검증도 생성과 동일하다. EQUAL은 `groupMemberIds`만, CUSTOM은 `memberAmounts`만 사용하고, 사용하지 않는 배열은 생략 또는 null로 전달한다.

균등 분할 요청:

```json
{
  "splitMode": "EQUAL",
  "totalAmount": 500,
  "groupMemberIds": [13, 11, 12]
}
```

회원별 금액 지정 요청:

```json
{
  "splitMode": "CUSTOM",
  "totalAmount": 20000,
  "memberAmounts": [
    { "groupMemberId": 11, "amount": 12000 },
    { "groupMemberId": 12, "amount": 8000 }
  ]
}
```

#### 성공 응답

HTTP 200, `SETTLEMENT200_1`로 `ApiResponse<SettlementResDTO.Preview>`를 반환한다.

```json
{
  "isSuccess": true,
  "code": "SETTLEMENT200_1",
  "message": "정산 분할 미리보기에 성공했습니다.",
  "result": {
    "splitMode": "EQUAL",
    "totalAmount": 500,
    "targetCount": 3,
    "items": [
      { "groupMemberId": 11, "nickname": "모이지", "amount": 167, "remainderAmount": 1 },
      { "groupMemberId": 12, "nickname": "민지", "amount": 167, "remainderAmount": 1 },
      { "groupMemberId": 13, "nickname": "도윤", "amount": 166, "remainderAmount": 0 }
    ]
  }
}
```

- `targetCount`는 검증된 대상 수이며 `items` 길이와 같다. `items`는 `groupMemberId` 오름차순이다.
- `nickname`은 조회 시점의 회원 닉네임이다. 이름을 요청 본문으로 받지 않는다.
- `amount`는 나머지 추가분이 이미 포함된 최종 청구액이다. 프론트에서 `remainderAmount`를 다시 더하지 않는다.
- `remainderAmount`는 EQUAL에서 0 또는 1원이고 CUSTOM에서는 항상 0원이다. 모든 `amount`의 합계는 `totalAmount`와 같다.

#### 오류 응답

[정산 생성 오류 응답 계약](#정산-생성-오류-응답-계약)의 공통 인증·권한·모임·DTO·JSON 오류와 `SETTLEMENT400_2`(대상), `SETTLEMENT400_3`(조건부 필드), `SETTLEMENT400_4`(금액)를 재사용한다. 일정·기한을 받지 않으므로 `SETTLEMENT400_1`과 `SETTLEMENT400_5`는 미리보기에서 사용하지 않는다.

#### Swagger 테스트

1. API 서버의 Swagger에 인증 쿠키가 있는 상태에서 `GET /api/v1/auth/csrf`를 실행한다.
2. 응답 `result.token`을 복사하고 미리보기의 `X-XSRF-TOKEN` 입력란에 넣는다. 발급된 `XSRF-TOKEN` 쿠키도 요청에 함께 전송되어야 한다.
3. `groupId`와 EQUAL 또는 CUSTOM 요청을 입력한다. Swagger 예시의 대상 ID는 실제 모임의 `group_member.id`로 바꾼다.
4. HTTP 200 / `SETTLEMENT200_1`과 회원별 금액을 확인한다. 미리보기만 실행하면 정산은 생성되지 않는다.

### 프론트 연동 흐름

1. 회원 선택·총액 입력 중에는 프론트에서 같은 분할 규칙으로 예상 금액을 즉시 표시한다. 체크할 때마다 API를 호출하지 않는다.
2. 정산 내용 적용 등 확인 동작에서 미리보기 API를 호출한다.
3. 확인 화면·모달에서 서버가 반환한 회원별 최종 금액과 1원 추가 대상을 표시한다.
4. 이후 총액·대상·분할 방식·개별 금액이 바뀌면 기존 미리보기를 무효화하고 다시 확인한다. 현재 입력과 다른 과거 비동기 응답은 적용하지 않는다.
5. 최종 확인 시 생성 API를 호출한다. 서버는 권한·대상·금액을 다시 검증하고 같은 규칙으로 계산하여 저장한다.

미리보기는 예약이 아니다. 생성 전에 대상이 유효하지 않게 바뀌면 생성 요청을 거부한다. 입력 중에는 `1인당 167원`만 표시해 총액이 501원으로 오해되지 않도록 금액 범위나 나머지를 안내한다.

## 정산 목록·상세 조회

목록 조회는 DTO·성공/오류 코드·Repository·Service·Converter·HTTP 엔드포인트·Swagger까지 구현되어 호출할 수 있다. 상세 조회는 DTO와 응답 코드만 정의되어 있으며 실제 조회 처리와 HTTP 엔드포인트는 아직 구현되지 않았다.

`SettlementReqDTO.Search`는 `page`·`size`가 null이면 각각 0·20을 적용하고 페이지 범위와 양수 일정 ID를 검증한다. `status`는 기존 `SettlementStatus` Enum을 사용한다. 쿼리 파라미터는 MVC 바인딩으로 수신하며 JSON 요청용 deserializer는 사용하지 않는다. 응답은 `SettlementResDTO.Page`·`Summary`·`Detail`·`Creator`·`Item`으로 구분하고 날짜는 초 단위 문자열로 직렬화한다.

### 공통 조회 권한과 데이터 기준

- JWT HttpOnly 쿠키로 인증한다. GET 요청이므로 CSRF 쿠키·헤더는 필요하지 않다.
- 해당 모임의 활성 회원이면 `OWNER`·`MANAGER`·`MEMBER` 모두 조회할 수 있다. 정산 대상이 아닌 회원도 같은 범위로 조회한다.
- 일반 회원에게도 전체 대상자의 청구 금액과 청구 상태를 공개한다. 본인 청구만으로 결과를 제한하지 않는다.
- 탈퇴·강퇴·삭제된 모임 회원, 삭제된 회원 계정과 비회원은 조회할 수 없다.
- 폐쇄된 모임도 기존 정산을 조회할 수 있다. 삭제된 모임은 조회할 수 없다. 폐쇄 모임에서 생성·미리보기를 허용한다는 의미는 아니다.
- 삭제된 정산은 목록·상세에서 제외한다. `OPEN`·`CLOSED`·`CANCELLED` 상태는 조회 대상이며 목록의 상태 필터로 제한할 수 있다.
- 정산 생성 이후 대상자가 탈퇴·강퇴되거나 삭제되어도 저장된 청구 항목과 금액은 유지한다. 조회 시 대상자의 현재 활성 여부로 기존 청구 항목을 제외하지 않는다.
- 닉네임은 조회 시점의 값이며 생성 당시 닉네임을 보존한 값이 아니다. 삭제된 계정의 닉네임 표시 방식은 [미정 계약](#미정-계약)에서 별도로 정의한다.
- 금액·ID는 기존 계약과 동일하게 JSON 숫자로 반환한다. 날짜는 한국 시간(`Asia/Seoul`)의 `yyyy-MM-dd'T'HH:mm:ss` 형식으로 반환한다.

### 정산 목록 조회

`GET /api/v1/groups/{groupId}/settlements`

#### 요청

요청 본문 없이 경로의 `groupId`와 선택 쿼리 파라미터를 사용한다.

| 파라미터 | 타입 | 규칙 |
| --- | --- | --- |
| `page` | integer | 기본값 0. 0부터 시작하는 페이지 번호, 0 이상 |
| `size` | integer | 기본값 20. 1~100 |
| `status` | string | 선택. `OPEN`·`CLOSED`·`CANCELLED`. 생략하면 전체 상태 |
| `scheduleId` | integer | 선택. 양의 정수. 해당 일정에 연결된 정산만 조회하며 생략하면 일정 연결 여부와 무관하게 조회 |

```http
GET /api/v1/groups/1/settlements?page=0&size=20&status=OPEN&scheduleId=7
```

- 필터를 함께 전달하면 모든 조건을 만족하는 정산을 조회한다.
- 일정 필터는 정산에 저장된 `scheduleId`만 비교한다. 일정의 존재·삭제 여부를 추가 검증하지 않는다. 존재하지 않거나 다른 모임에 속한 일정 ID는 빈 목록을 반환하고, 삭제된 일정에 연결된 기존 정산은 조회할 수 있다.
- 생성 시각 내림차순, 같은 생성 시각에서는 정산 ID 내림차순으로 정렬한다. 별도 정렬 파라미터는 제공하지 않는다.
- `totalElements`는 해당 모임에서 필터를 적용한 전체 정산 수이며 `totalPages`는 이를 `size`로 나눈 전체 페이지 수다.
- 결과가 없거나 마지막 페이지 이후를 요청하면 HTTP 200과 빈 `items`를 반환한다. 결과가 전혀 없으면 `totalElements=0`, `totalPages=0`, `hasNext=false`다.

#### 성공 응답

HTTP 200, `SETTLEMENT200_2`를 사용한다. 목록에는 정산 요약과 페이지 정보만 포함하며 본인 청구 정보를 별도로 제공하지 않는다.

```json
{
  "isSuccess": true,
  "code": "SETTLEMENT200_2",
  "message": "정산 목록 조회에 성공했습니다.",
  "result": {
    "items": [
      {
        "settlementId": 31,
        "title": "러닝 후 식사비",
        "scheduleId": 7,
        "status": "OPEN",
        "splitMode": "EQUAL",
        "totalAmount": 10000,
        "targetCount": 3,
        "dueAt": "2026-11-01T23:59:59",
        "createdAt": "2026-10-20T14:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

`targetCount`는 저장된 청구 항목 수다. 일정에 연결되지 않은 정산의 `scheduleId`와 납부 기한이 없는 정산의 `dueAt`는 null이다.

#### 내부 목록 조회 흐름

`SettlementQueryService.getSettlements`는 `@Transactional(readOnly = true)` 안에서 다음 순서로 처리한다.

1. 모임이 존재하고 삭제되지 않았는지 확인한다. `CLOSED`를 포함해 모임 상태 자체로 조회를 제한하지 않는다.
2. 요청자의 모임 회원 상태가 `ACTIVE`이고 모임 회원 정보와 연결된 계정이 삭제되지 않았는지 확인한다. 역할이나 청구 대상 여부는 제한하지 않는다.
3. `SettlementRepository.findPageByGroup`에서 모임·정산 삭제 여부·상태·일정 필터를 적용하고 생성 시각·ID 내림차순으로 페이지를 조회한다. 전체 건수에도 같은 필터를 사용한다.
4. 현재 페이지의 정산 ID들로 청구 항목 수를 한 번에 집계한다. 대상자의 현재 활성·삭제 여부로 집계를 제한하지 않으며, 빈 페이지에서는 집계 쿼리를 실행하지 않는다.
5. `SettlementConverter.toPage`·`toSummary`로 요약과 페이지 정보를 조립한다. 목록을 표시하기 위해 생성자·대상자의 닉네임이나 청구 항목 전체를 로딩하지 않는다.

실제 MySQL 테스트에서 필터·정렬·페이지 집계·기존 청구 인원수 보존과 활성 일반 회원·폐쇄 모임의 조회, 비활성·삭제 요청자의 접근 거부를 검증한다.

#### HTTP 입력 검증과 Swagger 테스트

- `SettlementController.getSettlements`는 경로의 `groupId`와 `@ModelAttribute SettlementReqDTO.Search`로 받은 쿼리 조건, JWT 회원 ID를 QueryService에 전달한다. 요청 본문은 받지 않는다.
- `SettlementControllerDocs`에 경로 ID의 `@Positive`와 요청 DTO의 `@Valid`를 선언하고 실제 Controller에는 `@Validated`를 적용한다. 생성·미리보기의 `@Valid`도 같은 인터페이스에 선언해 메서드 검증 계약을 일관되게 상속한다. DTO 필드 제약은 DTO에 유지하며 경로 검증 실패는 기존 `ConstraintViolationException` 처리로 `VALID400_1`과 필드별 메시지를 반환한다.
- 쿼리의 범위 위반은 `VALID400_1`로 반환한다. 숫자·Enum 변환 실패와 경로 ID의 숫자 변환 실패는 `COMMON400_1`, `result=null`로 반환한다. 공통 예외 처리기가 바인딩 실패와 필드 제약 위반을 구분하며, 변환 실패를 500 오류로 노출하지 않는다.
- 실제 JWT 필터를 사용하는 HTTP 통합 테스트에서 역할별 정상 조회, CSRF 없는 GET, 폐쇄 모임 조회, 인증 실패·접근 거부·모임 없음·입력 오류와 OpenAPI 스키마를 검증한다.

Swagger에서는 다음 순서로 확인한다.

1. API를 호출하는 호스트에 유효한 `moeasy_access_token` 쿠키가 있어야 한다.
2. 목록 조회의 `groupId`를 입력하고 필요에 따라 `page`·`size`·`status`·`scheduleId`를 지정한다. CSRF 발급이나 헤더 입력은 필요하지 않다.
3. HTTP 200 / `SETTLEMENT200_2`와 `items`·페이지 정보를 확인한다. 조회 결과가 없으면 빈 목록을 반환한다.

브라우저 프론트의 교차 출처 호출에는 `credentials: 'include'` 또는 `withCredentials: true`가 필요하다.

### 정산 상세 조회

`GET /api/v1/groups/{groupId}/settlements/{settlementId}`

#### 요청과 조회 기준

- 요청 본문과 별도 쿼리 파라미터 없이 경로의 `groupId`·`settlementId`를 사용한다.
- 정산이 해당 모임에 속하는지 확인한다. 다른 모임의 정산 ID를 전달하면 정산 없음과 동일하게 처리한다.
- 저장된 정산 정보와 전체 청구 항목을 반환하며, 청구 항목은 `groupMemberId` 오름차순으로 정렬한다.
- 각 청구 항목의 `settlementItemId`를 반환한다. 본인 청구 식별자를 별도 최상위 필드로 제공하지 않는다.

#### 성공 응답

HTTP 200, `SETTLEMENT200_3`을 사용한다.

```json
{
  "isSuccess": true,
  "code": "SETTLEMENT200_3",
  "message": "정산 상세 조회에 성공했습니다.",
  "result": {
    "settlementId": 31,
    "groupId": 1,
    "scheduleId": 7,
    "title": "러닝 후 식사비",
    "description": "식사비 정산입니다.",
    "status": "OPEN",
    "splitMode": "EQUAL",
    "totalAmount": 10000,
    "targetCount": 3,
    "dueAt": "2026-11-01T23:59:59",
    "createdAt": "2026-10-20T14:00:00",
    "createdBy": {
      "groupMemberId": 11,
      "nickname": "정규"
    },
    "items": [
      { "settlementItemId": 101, "groupMemberId": 11, "nickname": "정규", "amount": 3334, "status": "PENDING" },
      { "settlementItemId": 102, "groupMemberId": 12, "nickname": "민지", "amount": 3333, "status": "PENDING" },
      { "settlementItemId": 103, "groupMemberId": 13, "nickname": "도윤", "amount": 3333, "status": "PENDING" }
    ]
  }
}
```

- `targetCount`는 `items` 길이와 같다. 항목의 `status`는 `PENDING`·`PAID`·`REFUNDED`·`CANCELLED` 중 저장된 청구 상태다.
- `scheduleId`·`description`·`dueAt`에 저장된 값이 없으면 해당 필드는 null이다.
- `amount`는 저장된 최종 청구액이다. 미리보기와 달리 `remainderAmount`는 반환하지 않으며 조회 시 금액을 재분할하지 않는다.
- 납부액·미납 잔액·납부 기록·영수증·송금 링크는 해당 기능 구현 시 계약에 추가한다. 미구현 데이터를 임의의 0원이나 빈 값으로 제공하지 않는다.
- 본인 청구 여부 표시가 필요한 경우의 식별 계약은 후속 납부 기능에서 정의한다.

### 조회 오류 응답 계약

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| JWT 누락·만료·유효하지 않음 | 401 | `AUTH401_1` |
| 활성 모임 회원이 아님 | 403 | `AUTH403_1` |
| 모임 없음·삭제 | 404 | `GROUP404_1` |
| 상세의 정산 없음·삭제 또는 다른 모임의 정산 | 404 | `SETTLEMENT404_1` |
| 양수 ID·페이지 번호·페이지 크기 범위 검증 실패 | 400 | `VALID400_1` |
| 숫자 변환 실패·범위 초과·유효하지 않은 상태 필터 | 400 | `COMMON400_1` |

`SETTLEMENT404_1`의 메시지는 `정산을 찾을 수 없습니다.`로 정의한다. 조회는 CSRF 검증을 요구하지 않으며, 폐쇄 상태 자체를 모임 없음으로 처리하지 않는다. 목록의 일정 필터는 연결 ID만 비교하므로 일정 없음·삭제를 오류로 반환하지 않는다.

## 정산 생성 오류 응답 계약

정산 전용 코드와 `SettlementException`을 사용한다. 계산기는 중복·유효하지 않은 대상 ID에 `SETTLEMENT400_2`, 금액·합계·오버플로 오류에 `SETTLEMENT400_4`를 사용한다. 서비스는 권한·일정·회원 상태·납부 기한을 검증하며, Controller에서 발생한 DTO 검증 오류와 서비스 오류는 기존 공통 예외 처리 흐름으로 반환한다. JWT·CSRF 오류는 기존 시큐리티 필터의 공통 응답을 사용한다.

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| JWT 누락·만료·유효하지 않음 | 401 | 기존 인증 오류 코드 |
| 생성 권한 없음 / CSRF 검증 실패 | 403 | 기존 시큐리티·공통 권한 오류 코드 |
| 모임 없음·폐쇄·삭제 | 404 | `GROUP404_1` |
| 일정 없음·삭제 또는 다른 모임의 일정 | 400 | `SETTLEMENT400_1` |
| 중복·유효하지 않은 대상 회원 | 400 | `SETTLEMENT400_2` |
| 분할 방식과 조건부 필드 조합 불일치 | 400 | `SETTLEMENT400_3` |
| 0원 청구 발생·합계 범위 초과·총액과 개별 금액 합계 불일치 | 400 | `SETTLEMENT400_4` |
| 납부 기한이 생성 시각 이전이거나 같은 시각 | 400 | `SETTLEMENT400_5` |
| 단순 필수값·빈 대상 배열·길이·양수 검증 실패 | 400 | `VALID400_1` |
| 잘못된 JSON 타입·소수·Long 범위 초과·잘못된 날짜/분할 방식 형식 | 400 | `COMMON400_1` |

## 현재 데이터 구조

Flyway 초기 스키마에는 다음 테이블이 존재한다. `Settlement`, `SettlementItem` 엔티티와 분할 방식·상태 Enum, DTO·오류 코드, 금액 계산기, 정산 생성·미리보기·목록 조회 API는 구현되어 있다. 상세 조회 API와 납부·영수증 엔티티는 후속 기능에서 구현한다.

| 테이블 | 현재 저장 정보 |
| --- | --- |
| `settlement` | 모임·일정·회비 규칙 연결, 생성자 모임 회원, 제목·설명·기한·총액·분할 방식·상태 |
| `settlement_item` | 정산별 대상 모임 회원·청구 금액·상태. `(settlement_id, group_member_id)` 유니크 제약 |
| `settlement_payment` | 청구 항목별 납부·환불 기록, 금액·상태·발생 시각·멱등 키, 선택적 계좌 거래 연결 |
| `receipt` | 정산별 이미지 키·상호명·금액·결제 시각 |

`V20261019003905__add_settlement_total_amount_and_split_mode.sql`은 생성 당시 총액과 분할 방식을 보존할 `total_amount`, `split_mode` 필수 컬럼을 추가한다. 총액과 회원별 청구액은 DB에서도 1~9,223,372,036,854,775,807원으로 제한하며, 분할 방식은 `EQUAL`·`CUSTOM`만 허용한다. 총액과 청구액 합계의 일치 여부는 계산기에서 보장하며 생성 서비스는 검증된 계산 결과를 저장한다.

이 마이그레이션은 정산 기능 도입 전의 빈 정산 테이블을 대상으로 한다. 기존 정산 행이 있으면 총액·분할 방식을 임의로 추정하지 않고 실패한다. 해당 환경은 기존 데이터의 이관 정책을 정한 후 적용해야 한다.

`Settlement`는 `BaseEntity`의 생성·수정·삭제 시각을 사용한다. `settlement_item`에는 삭제 시각 컬럼이 없으므로 `SettlementItem`은 생성·수정 시각만 매핑한다. 두 엔티티의 생성 기본 상태는 각각 `OPEN`, `PENDING`이며, 현재 단계에서는 상태 변경 메서드를 제공하지 않는다. 모임·생성자·대상 회원은 지연 로딩 연관 관계를 사용하고, 아직 도메인이 없는 일정·회비 규칙은 ID로 매핑하여 기존 DB 외래키를 유지한다.

정산 상태는 `OPEN`·`CLOSED`·`CANCELLED`, 회원별 청구 상태는 `PENDING`·`PAID`·`REFUNDED`·`CANCELLED`로 관리한다. 초안 저장은 지원하지 않는다. 미납과 부분 납부는 모두 `PENDING`이며, 청구액을 전액 납부했을 때 `PAID`로 구분한다. 납부 금액·잔액은 후속 납부 기능에서 납부·환불 기록을 바탕으로 처리한다.

같은 마이그레이션에서 상태 제약을 위 목록으로 변경하고 기존 `PARTIALLY_PAID`를 `PENDING`으로 전환한다.

일정과 RSVP의 JPA 엔티티는 아직 없다. 정산 생성에서는 `ScheduleRepository`의 SQL 조회로 일정 소속·삭제 여부를 확인한다. `Schedule` 엔티티 구현 시 이 조회를 JPA 기반으로 전환하도록 TODO를 남겨두었다. 참석 예정 회원 조회는 회원 목록 API에서 후속 구현하며, 일정 CRUD 전체를 정산 기능에 포함하지 않는다.

## 미정 계약

다음 항목은 해당 기능 구현 전에 구체화하고 이 문서에 반영한다.

- 회원 목록 조회의 성공·오류 코드와 일정 필터 오류 계약.
- 정산 조회에서 삭제된 회원 계정의 닉네임 표시 방식. 생성자와 청구 대상자 모두에 적용한다.

## 후속 기능

아래 기능은 아직 구현되지 않았다. 구현할 때 API 계약과 상태·데이터 구조를 이 문서에 추가한다.

- 납부 기록·부분 납부·납부 취소·환불 및 중복 요청 방지.
- 정산 생성 요청의 중복 방지: 요청 식별 방식, 동시 요청·내용 변경·재시도 시 처리와 기존 결과 재응답 계약.
- 영수증 이미지 업로드·등록·조회·삭제.
- 정산 수정·취소·마감과 납부 이력이 있는 정산의 변경 정책.
- 하이브리드 정산: 최초 모금과 실제 지출을 연결하고 초과 지출을 별도 청구한다. 기존 미납금과 추가 청구를 구분한다.
- 계좌 연동·거래내역 대조·정기 회비 자동 생성·납부 알림·정산 공유·PDF/엑셀 내보내기.
- 토스 앱 딥링크를 통한 송금 화면 이동. 앱 실행·복귀만으로 납부 완료를 확정하지 않으며, 납부 확인 절차는 별도로 정의한다.
