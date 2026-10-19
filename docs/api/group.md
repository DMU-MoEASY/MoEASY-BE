# Group API 명세

## 범위와 공통 계약

Group API의 공통 접두사는 `/api/v1`이다. 아래 표의 `/groups` 경로에는 모두 이 접두사가 적용된다. Issue #14의 그룹 CRUD와 Issue #23의 승인제 가입 관리 API를 제공한다. 목록·검색·초대 및 참여/미참여 조회는 [후속 범위](#후속-범위)에 보존한다.

- Group API의 식별자 `groupId`는 `member_group.id`에 대응하는 양의 정수다.
- 모든 API는 Issue #15의 JWT로 인증된 회원만 호출할 수 있다. 상세 조회에는 Group 멤버십을 요구하지 않는다. 수정과 폐쇄는 해당 Group의 활성 `OWNER` 멤버십을 요구한다.
- 명세의 기존 `STAFF`는 DB 역할 `MANAGER`를 뜻한다. 가입 관리의 목록·승인·거절에는 삭제되지 않은 활성 OWNER·MANAGER 권한이 필요하다. 역할 임명·회수 API는 포함하지 않는다.
- 정상·오류 응답은 공통 `ApiResponse<T>` 형식을 따른다. 아래 Group 코드는 `GroupSuccessCode`와 `GroupErrorCode`에 정의되어 있다.

| Method | Endpoint | 기능 | 권한 |
| --- | --- | --- | --- |
| POST | /groups | Group 생성 및 생성자 OWNER 멤버십 부여 | 인증 회원 |
| GET | /groups/{groupId} | Group 단건 상세 조회 | 인증 회원 |
| PATCH | /groups/{groupId} | 전달된 Group 필드만 변경 | 활성 OWNER |
| DELETE | /groups/{groupId} | Group 소프트 삭제와 삭제 이력 기록 | 활성 OWNER |

## 생성

`POST /groups` 요청:

```json
{
  "name": "퇴근 후 한강 러닝",
  "description": "초보자도 함께 달리는 주 1회 러닝 모임입니다.",
  "categoryId": 1,
  "regionCode": "11680",
  "placeName": "한강공원",
  "address": "서울특별시 영등포구 여의동로",
  "latitude": 37.5665000,
  "longitude": 126.9780000,
  "maxMembers": 30,
  "joinPolicy": "APPROVAL",
  "coverImageKey": "group-covers/123/e7f3d5a2-6c1b-4a89-9d20-5f1c8b2e4a60"
}
```

`name`, `description`, 숫자 `categoryId`, `regionCode`, `placeName`, `address`, `maxMembers`, `joinPolicy`는 필수다. 길이·허용값은 `member_group`의 컬럼과 제약을 따른다. `categoryId`는 존재하며 삭제되지 않은 `category.id`여야 한다. `latitude`와 `longitude`는 둘 다 생략하거나 둘 다 제공한다. 한쪽만 제공하면 HTTP 400, `GROUP400_2`로 거부한다. 값의 범위는 각각 -90~90, -180~180이다. `coverImageKey`는 선택 사항이다.

생성자는 같은 DB 트랜잭션에서 `group_member`에 `ACTIVE/OWNER`로 저장한다. 둘 중 한 저장이라도 실패하면 모두 롤백한다. 성공 시 HTTP 201, `GROUP201_1`로 `groupId`를 반환한다.

`result` 예시: `{"groupId": 1}`

## 단건 상세

`GET /groups/{groupId}`는 인증된 회원에게 Group 정보를 반환한다. 해당 Group의 멤버인지 여부는 이 API의 조건이 아니다. `deleted_at`이 있거나 `status=CLOSED`인 Group은 반환하지 않으며, 존재하지 않는 Group과 동일한 `GROUP404_1` 응답을 사용한다.

성공 시 HTTP 200, `GROUP200_1`로 `groupId`, `categoryId`, 이름, 설명, 지역·장소·좌표, 정원, 가입 정책, 상태 및 `coverImageUrl`을 반환한다. `coverImageUrl`은 커버 키가 없으면 `null`이며, 있으면 비공개 S3 객체에 대한 만료되는 presigned GET URL이다. S3 객체 키나 영구 공개 URL은 응답에 포함하지 않는다. 이미지 URL 생성 기능의 선행 조건은 [커버 이미지](#커버-이미지)에 적는다.

`result` 필드: `groupId`, `categoryId`, `name`, `description`, `regionCode`, `placeName`, `address`, `latitude`, `longitude`, `maxMembers`, `joinPolicy`, `status`, `coverImageUrl`.

## 부분 수정

`PATCH /groups/{groupId}`는 요청에 **존재하는** 필드만 변경한다. 수정 가능한 필드는 `categoryId`, `name`, `coverImageKey`, `description`, `regionCode`, `placeName`, `address`, `latitude`, `longitude`다. `maxMembers`, `joinPolicy`, 상태, OWNER는 이 PATCH로 변경하지 않는다.

- 생략한 필드는 기존 값을 유지한다. 빈 객체 `{}`는 HTTP 400으로 거부한다.
- `categoryId`, `name`, `description`, `regionCode`, `placeName`, `address`에 명시적 `null`을 보내면 HTTP 400이다. Category 변경 시 존재하며 삭제되지 않았는지 확인한다.
- `coverImageKey: null`은 저장된 이미지 키를 제거한다. 값이 있으면 [커버 이미지](#커버-이미지)의 검증을 적용한다.
- 좌표는 `latitude`와 `longitude`를 함께 제공한다. 두 숫자는 함께 갱신하고, 두 필드 모두 `null`이면 좌표를 제거한다. 한쪽만 제공하거나 한쪽만 `null`이면 HTTP 400, `GROUP400_2`다.
- DTO 구현은 JSON 필드의 생략과 명시적 `null`을 구분해야 한다.

성공 시 HTTP 200, `GROUP200_2`로 변경 후 단건 상세와 같은 필드를 반환한다.

## 폐쇄

`DELETE /groups/{groupId}` 요청:

```json
{
  "reason": "운영 종료",
  "confirmationText": "퇴근 후 한강 러닝",
  "archiveContent": true
}
```

세 필드는 모두 필수다. `reason`은 공백이 아닌 최대 255자, `confirmationText`는 현재 Group 이름과 정확히 일치해야 하며 최대 100자다. 불일치하면 HTTP 400, `GROUP400_1`(`GROUP_CONFIRMATION_TEXT_MISMATCH`)로 응답하고 아무 변경도 저장하지 않는다. `archiveContent`는 전달값을 삭제 이력에 저장할 뿐 콘텐츠 아카이빙을 실행하지 않는다.

폐쇄는 물리 삭제하지 않는다. 한 트랜잭션에서 `member_group.deleted_at`에 폐쇄 시각을 기록하고 `status`를 `CLOSED`로 바꾸며 `group_deletion_history`에 요청값, 수행자 ID와 동일한 폐쇄 시각을 저장한다. 이력이 이미 있는 Group은 폐쇄된 Group으로 처리한다. 성공 시 HTTP 200, `GROUP200_3`, `result: null`을 반환한다.

## 오류 응답 계약

| 상황 | HTTP | 코드 | 메시지 |
| --- | --- | --- | --- |
| `confirmationText`가 현재 Group 이름과 다름 | 400 | `GROUP400_1` | 모임 이름 확인 문구가 일치하지 않습니다. |
| 위도·경도 중 한쪽만 제공하거나 한쪽만 `null` | 400 | `GROUP400_2` | 위도와 경도를 함께 입력해야 합니다. |
| 요청 형식·필수값·PATCH 빈 객체 또는 좌표 값 오류 | 400 | `VALID400_1` 또는 `COMMON400_1` | 공통 검증 오류 응답 |
| S3 저장소 장애 | 503 | `STORAGE503_1` | 저장소를 일시적으로 사용할 수 없습니다. |
| Group이 없거나 폐쇄됨 | 404 | `GROUP404_1` | 모임을 찾을 수 없습니다. |
| Category가 없거나 삭제됨 | 404 | `GROUP404_2` | 카테고리를 찾을 수 없습니다. |
| 수정·폐쇄 요청자가 OWNER가 아님 | 403 | `AUTH403_1` | 요청이 거부되었습니다. |
| JWT가 없거나 유효하지 않음 | 401 | Issue #15 인증 오류 코드 | Issue #15의 필터 응답 계약을 따른다. |

Group 전용 오류 코드는 `GroupErrorCode`에 정의되어 있다. 인증 오류는 Issue #15가 제공하는 필터·코드를 재사용한다.

## 커버 이미지

요청 필드 `coverImageKey`는 S3 객체 **키**이며 파일 ID나 URL이 아니다. 비어 있지 않은 최대 500자 문자열이어야 한다. 키는 별도 커버 이미지 업로드 발급 흐름에서 인증 회원에게 발급받아야 하며, Group CRUD 범위에는 이 키와 업로드 URL을 발급하는 HTTP API가 포함되지 않는다. 발급 키는 `group-covers/{memberId}/{UUID}` 형식이다. 비공개 버킷에 객체가 존재하고 해당 회원에게 발급된 키인지 저장 전에 확인한다. Group 내부 서비스는 발급 이력의 회원 ID와 S3 객체 존재·Content-Type을 검증한다.

S3 객체 검증은 Group 쓰기 트랜잭션 전에 수행한다. 수정 시에는 S3 검증 전에 Group 상태와 OWNER 권한을 미리 확인하고, 검증 후 비관적 락을 획득해 두 조건을 다시 확인한 뒤 변경한다. S3 객체가 검증 이후 변경되거나 삭제될 수 있으므로 S3와 DB 간 완전한 원자성은 보장하지 않는다.

S3 `HeadObject`가 403 또는 일반 404를 반환하면 해당 키만 목록 조회한다. 조회가 완료되고 정확한 키가 없을 때만 `COMMON400_1`로 거부한다. 목록 접근이 거부되거나 조회가 실패하거나 결과가 완결되지 않으면 객체 부재를 확정하지 않고 `STORAGE503_1`로 응답한다. 배포 자격 증명에는 `group-covers/*` 접두사와 최대 한 건으로 제한된 `s3:ListBucket` 권한이 필요하다.

응답에는 키 대신 `S3StorageService.createPresignedGetUrl`로 만든 `coverImageUrl`을 사용한다. URL은 설정된 유효 시간이 지나면 다시 조회해 발급받아야 한다. 이미지가 없으면 `null`이다. 비공개 S3 접근·서명 요청에는 요청과 데이터 전송 비용이 발생할 수 있다.

## 승인제 가입 관리 (Issue #23)

공통 경로는 `/api/v1/groups/{groupId}/join-requests`다. 그룹·회원·신청 식별자는 숫자 ID를 사용한다. 인증된 회원 ID는 JWT에서 가져오며, 요청의 회원·처리자 ID를 신뢰하지 않는다. 쿠키 인증 쓰기 요청은 기존 CSRF 쿠키·헤더 계약을 따른다.

| Method | 하위 경로 | 기능 | 권한 | 성공 코드 |
| --- | --- | --- | --- | --- |
| POST | 없음 | 가입 신청 | 인증 회원 | 201, `GROUP201_2` |
| DELETE | `/me` | 본인 대기 신청 취소 | 신청자 본인 | 200, `GROUP200_4` |
| GET | 없음 | 대기 신청 목록 | 활성 OWNER·MANAGER | 200, `GROUP200_5` |
| GET | `/me` | 본인 최근 신청 | 본인 | 200, `GROUP200_6` |
| POST | `/{requestId}/approve` | 승인 | 활성 OWNER·MANAGER | 200, `GROUP200_7` |
| POST | `/{requestId}/reject` | 거절 | 활성 OWNER·MANAGER | 200, `GROUP200_8` |

### 요청·응답

가입 신청 본문은 `{"introduction":"함께하고 싶어요"}`다. 소개글은 필수·공백 불가·최대 500자다. 성공 결과는 `requestId`, `status`(`PENDING`), `createdAt`이다.

취소와 승인은 본문이 없다. 거절 본문은 `{"rejectionReason":"현재 모집 대상과 다릅니다"}`이며 사유는 선택·최대 500자다. 사유 없이 `{}`도 허용한다. 취소·승인·거절 성공 결과는 모두 `null`이다.

목록은 `?page=0&size=20`으로 조회한다. 기본값은 페이지 0, 크기 20이며 페이지는 0 이상, 크기는 1~100이다. 숫자가 아닌 입력과 정수 범위 초과도 HTTP 400 및 공통 오류 형식으로 반환한다. 조회 offset인 `(long) page * size`는 `Integer.MAX_VALUE`(2,147,483,647) 이하여야 하며, 초과하면 조회 전에 HTTP 400, `COMMON400_1`로 거부한다. 예를 들어 `size=20`이면 `page=107374182`까지 허용하고 `page=107374183`부터 거부한다. PENDING만 `createdAt ASC, id ASC`로 조회한다.

```json
{
  "items": [
    {"requestId": 1, "memberId": 2, "nickname": "러너", "introduction": "소개", "createdAt": "2026-10-19T12:00:00"}
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

빈 페이지는 `items: []`, `hasNext: false`다. 총 개수는 반환하지 않는다.

본인 최근 신청은 `createdAt DESC, id DESC`의 첫 행을 선택한다. 결과 필드는 `requestId`, `status`, `introduction`, `rejectionReason`, `createdAt`, `processedAt`이며 이력이 없으면 `result: null`이다.

### 상태·권한·멤버십

- 신청은 ACTIVE·APPROVAL 그룹에서만 허용한다. OPEN 가입 신청은 HTTP 400이다.
- 승인·거절은 ACTIVE 그룹에서만 허용한다. ARCHIVED에서는 조회·본인 취소만 허용한다.
- 없는 그룹·삭제·폐쇄 그룹은 HTTP 404다. 다른 그룹의 신청 ID도 HTTP 404다.
- 목록·승인·거절 권한은 역할 OWNER 또는 MANAGER, 상태 ACTIVE, `deletedAt=null`을 모두 만족해야 한다. ACTIVE이지만 소프트 삭제된 운영진도 HTTP 403이다.
- 기존 그룹 수정·폐쇄는 OWNER 전용을 유지한다. 기존 `requireOwner()`의 삭제 여부 조건 누락은 별도 불일치이며, 신규 가입 관리 검증에서는 삭제 여부까지 검사한다.
- 정원에는 삭제되지 않은 ACTIVE 멤버십만 계산하며 OWNER·MANAGER도 포함한다. 대기 신청은 자리를 예약하지 않는다. 신청·승인 시 각각 정원을 확인한다.
- 삭제되지 않은 ACTIVE 회원의 신청은 이미 가입 오류로 거부한다. 동일 그룹 PENDING 신청도 중복 오류로 거부한다.
- LEFT와 `ACTIVE + deletedAt != null` 회원은 재신청·재가입 복구를 허용한다. KICKED는 삭제 여부와 무관하게 재신청을 차단한다.
- 재가입 승인 시 기존 멤버십 ID를 유지하고 `role=MEMBER`, `status=ACTIVE`, `deletedAt=null`, `joinedAt=승인 시각`으로 복구한다. 과거 OWNER·MANAGER 권한은 복원하지 않는다.
- 거절·취소 후에는 새 신청을 생성하고 기존 이력을 보존한다. 그룹 폐쇄 시에도 신청을 물리 삭제하지 않는다.

### 상태 전이·시각·동시성

PENDING만 APPROVED·REJECTED·CANCELLED로 전이할 수 있다. 승인·거절은 운영진 멤버십 ID와 처리 시각을 저장한다. 취소는 처리 시각을 저장하고 처리자 ID는 null이다.

신청의 생성·수정 시각은 JPA auditing으로 매핑하며 MySQL DATETIME(6) 정밀도로 맞춘다. 생성 응답의 `createdAt`은 DB 저장값과 일치한다. 승인에서는 한 번 얻은 시각을 신규·복구 멤버십의 `joinedAt`과 신청 `processedAt`에 함께 사용한다.

신청·승인·거절·취소는 기존 폐쇄와 같은 그룹 비관적 락을 먼저 획득하고 상태·권한·신청·정원을 검증한다. 승인 시 정원 확인, 멤버십 생성·복구, 신청 처리 정보를 하나의 트랜잭션에 저장하며 실패 시 모두 롤백한다. 먼저 커밋된 신청 상태 전이가 유효하고 후속 처리는 상태에 맞는 409를 반환한다. 폐쇄가 먼저 커밋되면 후속 가입 처리는 404다.

대기 신청 중복은 서비스 검사와 `uq_group_join_request_pending`으로 방어한다. 저장·flush에서 해당 유일 제약 충돌만 중복 신청 409로 변환하고 도메인 예외를 던져 롤백한다. 다른 무결성 오류는 중복 신청으로 변환하지 않는다. 이후 즉시 가입·초대 수락 등 인원 증가 경로도 동일 그룹 락을 따라야 한다.

### 가입 관리 오류 코드

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| 소개글·거절 사유·페이지 검증 실패 | 400 | `VALID400_1` 또는 `COMMON400_1` |
| OPEN 그룹 가입 신청 | 400 | `GROUP400_3` |
| 미인증·잘못된 JWT | 401 | `AUTH401_1` |
| 활성·미삭제 OWNER·MANAGER가 아님 | 403 | `AUTH403_1` |
| 그룹 없음·삭제·폐쇄 | 404 | `GROUP404_1` |
| 신청 없음·다른 그룹의 신청 | 404 | `GROUP404_3` |
| ARCHIVED에서 신청·승인·거절 | 409 | `GROUP409_1` |
| 이미 가입 | 409 | `GROUP409_2` |
| 중복 PENDING 신청·대기 신청 유일 제약 충돌 | 409 | `GROUP409_3` |
| 정원 초과 | 409 | `GROUP409_4` |
| KICKED 회원 | 409 | `GROUP409_5` |
| 이미 처리된 신청의 승인·거절 | 409 | `GROUP409_6` |
| 취소할 PENDING 없음 | 409 | `GROUP409_7` |

## 후속 범위

아래 항목은 이동 전 Group 명세의 내용을 보존한 것이다. Issue #14에서 구현하거나 이번 오류·권한 계약을 적용하지 않는다. 각 기능을 구현할 때 경로·권한·상태·응답을 재확정한다. 폐쇄 Group의 콘텐츠 비노출도 해당 콘텐츠 기능에서 적용한다.

| Method | Endpoint | 기능 | 기존 명세의 권한 |
| --- | --- | --- | --- |
| GET | /groups | 키워드·카테고리·지역·거리 기반 검색 | Public/USER |
| GET | /users/me/groups | 내 참여 모임 조회 | USER |
| POST | /groups/{groupId}/invite-links | 초대 링크·코드 생성 | GROUP_STAFF |
| GET | /group-invites/{inviteCode} | 초대 링크 유효성·모임 요약 조회 | Public |
| POST | /group-invites/{inviteCode}/accept | 초대 수락 또는 가입 신청 | USER |
| DELETE | /groups/{groupId}/invite-links/{inviteId} | 초대 링크 폐기 | GROUP_STAFF |

### 기존 검색 메모

`GET /groups?q=러닝&category=SPORTS&regionCode=11680&lat=37.5665&lng=126.9780&radiusKm=5&sort=DISTANCE&cursor=...&size=20`

- `sort`: RECOMMENDED, DISTANCE, NEWEST, MEMBER_COUNT
- 위치 권한이 없으면 `lat/lng/radiusKm`을 생략하고 지역 코드로 검색한다.
- 추천 점수의 내부 계산값은 노출하지 않고 추천 사유 태그만 반환한다.
