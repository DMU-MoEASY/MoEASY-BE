# Group API 명세

## 범위와 공통 계약

Group API의 공통 접두사는 `/api/v1`이다. 아래 표의 `/groups` 경로에는 모두 이 접두사가 적용된다. Issue #14의 그룹 CRUD, Issue #23의 승인제 가입 관리, Issue #38의 추천·검색 피드를 제공한다. 거리·기타 정렬·초대 및 참여/미참여 조회는 [후속 범위](#후속-범위)에 보존한다.

- Group API의 식별자 `groupId`는 `member_group.id`에 대응하는 양의 정수다.
- 모든 API는 JWT로 인증된 회원만 호출할 수 있다. 쿠키·Bearer 인증과 Swagger 사용법은 [공통 인증 계약](auth.md)을 따른다. 상세 조회에는 Group 멤버십을 요구하지 않는다. 수정과 폐쇄는 해당 Group의 활성 `OWNER` 멤버십을 요구한다.
- 명세의 기존 `STAFF`는 DB 역할 `MANAGER`를 뜻한다. 가입 관리의 목록·승인·거절에는 삭제되지 않은 활성 OWNER·MANAGER 권한이 필요하다. 역할 임명·회수 API는 포함하지 않는다.
- 정상·오류 응답은 공통 `ApiResponse<T>` 형식을 따른다. 아래 Group 코드는 `GroupSuccessCode`와 `GroupErrorCode`에 정의되어 있다.

| Method | Endpoint | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /groups?sort=RECOMMENDED | 추천·검색 피드 (10장 고정) | 인증 회원 |
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

성공 시 HTTP 200, `GROUP200_1`로 `groupId`, `categoryId`, 이름, 설명, 지역·장소·좌표, 정원, 가입 정책, 상태, 티어 및 `coverImageUrl`을 반환한다. `coverImageUrl`은 커버 키가 없으면 `null`이며, 있으면 비공개 S3 객체에 대한 만료되는 presigned GET URL이다. S3 객체 키나 영구 공개 URL은 응답에 포함하지 않는다. 이미지 URL 생성 기능의 선행 조건은 [커버 이미지](#커버-이미지)에 적는다.

`result` 필드: `groupId`, `categoryId`, `name`, `description`, `regionCode`, `placeName`, `address`, `latitude`, `longitude`, `maxMembers`, `joinPolicy`, `status`, `coverImageUrl`, `tier`.

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

### 업로드 URL 발급

`POST /api/v1/groups/cover-image-uploads`는 인증 회원에게 Group 커버용 S3 PUT URL을 발급한다. 요청 본문은 다음과 같다.

```json
{"contentType":"image/jpeg"}
```

허용 형식은 `image/jpeg`, `image/png`, `image/webp`다. 성공은 HTTP 201 / `GROUP201_2`이며 `result`에 `coverImageKey`, `uploadUrl`, `uploadUrlExpiresAt`, `requiredHeaders`를 포함한다. `uploadUrlExpiresAt`은 S3 presigner가 해당 URL에 반환한 만료 시각이다. `requiredHeaders`에는 실제 서명된 요청 헤더를 담고, URL로부터 HTTP 클라이언트가 정하는 `host`는 포함하지 않는다. 클라이언트는 URL에 PUT할 때 응답의 `requiredHeaders`를 그대로 전송한다. 키는 `group-covers/{memberId}/{UUID}` 형식이고 회원에게 발급 이력을 저장한다.

```json
{
  "isSuccess": true,
  "code": "GROUP201_2",
  "message": "커버 이미지 업로드 URL을 발급했습니다.",
  "result": {
    "coverImageKey": "group-covers/42/123e4567-e89b-12d3-a456-426614174000",
    "uploadUrl": "<presigned PUT URL>",
    "uploadUrlExpiresAt": "2026-01-01T00:05:00Z",
    "requiredHeaders": {"Content-Type":"image/jpeg"}
  }
}
```

### 발급 키 정책

- Group 전용 키와 발급 이력을 사용한다. S3 URL 서명 및 객체 메타데이터 조회는 공유 인프라 `S3StorageService`가 담당한다.
- URL 만료 기본값은 5분이다. 이 만료는 URL을 통한 PUT 권한에만 적용하며, 발급 키를 Group에 연결할 수 있는 기간에는 별도 기한을 두지 않는다.
- 같은 회원은 키를 재사용해 여러 Group에 연결할 수 있다.
- 자동 정리는 수행하지 않는다. 이미지 제거는 Group의 키 참조만 제거하며 S3 객체를 삭제하지 않는다.
- 객체 존재 확인 시 S3 Content-Type 메타데이터와 발급 이력의 Content-Type을 비교한다. 파일 바이트, 실제 이미지 형식·정상 디코딩, 크기·해상도, 악성 콘텐츠는 확인하지 않는다.
- 같은 PUT URL은 만료 전 다시 사용할 수 있으며 동일 키 객체를 덮어쓸 수 있다. 따라서 메타데이터 확인과 Group 저장 사이, 저장 이후에도 객체가 바뀔 수 있고 이 일련의 작업은 S3와 DB 사이 원자성을 제공하지 않는다.

S3 객체 검증은 Group 쓰기 트랜잭션 전에 수행한다. 수정 시에는 S3 검증 전에 Group 상태와 OWNER 권한을 미리 확인하고, 검증 후 비관적 락을 획득해 두 조건을 다시 확인한 뒤 변경한다.

S3 `HeadObject`가 403 또는 일반 404를 반환하면 해당 키만 목록 조회한다. 조회가 완료되고 정확한 키가 없을 때만 HTTP 400 / `GROUP400_4`로 거부한다. 목록 접근이 거부되거나 조회가 실패하거나 결과가 완결되지 않으면 객체 부재를 확정하지 않고 `STORAGE503_1`로 응답한다. 배포 자격 증명에는 `group-covers/*` 접두사와 최대 한 건으로 제한된 `s3:ListBucket` 권한이 필요하다.

미발급 키와 타인 소유 키는 HTTP 400 / `GROUP400_3`으로 동일하게 반환해 타인 발급 이력의 존재를 노출하지 않는다. 발급 회원의 키에 객체가 없으면 HTTP 400 / `GROUP400_4`, Content-Type이 발급 정보와 다르면 HTTP 400 / `GROUP400_5`다. 지원하지 않는 형식은 HTTP 400 / `GROUP400_6`, 저장소 장애 또는 객체 부재를 확정할 수 없는 경우는 HTTP 503 / `STORAGE503_1`이다.

응답에는 키 대신 `S3StorageService.createPresignedGetUrl`로 만든 `coverImageUrl`을 사용한다. URL은 설정된 유효 시간이 지나면 다시 조회해 발급받아야 한다. 이미지가 없으면 `null`이다. 비공개 S3 접근·서명 요청에는 요청과 데이터 전송 비용이 발생할 수 있다.

## 승인제 가입 관리 (Issue #23)

공통 경로는 `/api/v1/groups/{groupId}/join-requests`다. 그룹·회원·신청 식별자는 숫자 ID를 사용한다. 인증된 회원 ID는 JWT에서 가져오며, 요청의 회원·처리자 ID를 신뢰하지 않는다. 쿠키 인증 쓰기 요청은 기존 CSRF 쿠키·헤더 계약을 따른다.

| Method | 하위 경로 | 기능 | 권한 | 성공 코드 |
| --- | --- | --- | --- | --- |
| POST | 없음 | 가입 신청 | 인증 회원 | 201, `GROUP201_3` |
| DELETE | `/me` | 본인 대기 신청 취소 | 신청자 본인 | 200, `GROUP200_4` |
| GET | 없음 | 대기 신청 목록 | 활성 OWNER·MANAGER | 200, `GROUP200_5` |
| GET | `/me` | 본인 최근 신청 | 본인 | 200, `GROUP200_6` |
| POST | `/{requestId}/approve` | 승인 | 활성 OWNER·MANAGER | 200, `GROUP200_7` |
| POST | `/{requestId}/reject` | 거절 | 활성 OWNER·MANAGER | 200, `GROUP200_8` |

### 요청·응답

가입 신청 본문은 `{"introduction":"함께하고 싶어요"}`다. 소개글은 필수·공백 불가·최대 500자다. 성공 결과는 `requestId`, `status`(`PENDING`), `createdAt`이다.

취소와 승인은 본문이 없다. 거절 본문은 `{"rejectionReason":"현재 모집 대상과 다릅니다"}`이며 사유는 선택·최대 500자다. 사유 없이 `{}`도 허용한다. 취소·승인·거절 성공 결과는 모두 `null`이다.

신청·취소·승인·거절 요청에는 `X-XSRF-TOKEN` 헤더가 필수다. 먼저 `GET /api/v1/auth/csrf`를 호출하고 응답의 `result.token`을 헤더에 전달하며, 발급된 `XSRF-TOKEN` 쿠키도 유지한다. 토큰이 없거나 일치하지 않으면 HTTP 403으로 거부한다.

목록은 `?page=0&size=20`으로 조회한다. 기본값은 페이지 0, 크기 20이며 페이지는 0 이상, 크기는 1~100이다. 숫자가 아닌 입력과 정수 범위 초과는 HTTP 400, `COMMON400_1`, “잘못된 요청입니다.”, `result: null`로 반환하며 내부 타입·변환 예외 메시지는 노출하지 않는다. 단순 범위 위반(`page=-1`, `size=0/101`)은 `VALID400_1`로 반환한다. 조회 offset인 `(long) page * size`는 `Integer.MAX_VALUE`(2,147,483,647) 이하여야 하며, 초과하면 조회 전에 HTTP 400, `COMMON400_1`로 거부한다. 예를 들어 `size=20`이면 `page=107374182`까지 허용하고 `page=107374183`부터 거부한다. PENDING만 `createdAt ASC, id ASC`로 조회한다.

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
| OPEN 그룹 가입 신청 | 400 | `GROUP400_7` |
| 미인증·잘못된 JWT | 401 | `AUTH401_1` |
| 활성·미삭제 OWNER·MANAGER가 아님 | 403 | `AUTH403_1` |
| 신청 시 JWT의 회원이 DB에 없음 | 404 | `MEMBER404_1` |
| CSRF 토큰 누락·불일치 (신청·취소·승인·거절) | 403 | `AUTH403_1` |
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
| GET | /groups (후속 확장) | 거리 필터·거리/최신/인원 정렬 | 재확정 필요 |
| GET | /users/me/groups | 내 참여 모임 조회 | USER |
| POST | /groups/{groupId}/invite-links | 초대 링크·코드 생성 | GROUP_STAFF |
| GET | /group-invites/{inviteCode} | 초대 링크 유효성·모임 요약 조회 | Public |
| POST | /group-invites/{inviteCode}/accept | 초대 수락 또는 가입 신청 | USER |
| DELETE | /groups/{groupId}/invite-links/{inviteId} | 초대 링크 폐기 | GROUP_STAFF |

### 미구현 검색 확장 메모

아래는 과거 기획 예시이며 현재 호출 계약이 아니다. 현재 추천 조회는 아래 #38 계약을 사용한다.

`GET /groups?...&lat=37.5665&lng=126.9780&radiusKm=5&sort=DISTANCE`

- `sort`: RECOMMENDED, DISTANCE, NEWEST, MEMBER_COUNT
- 위치 권한이 없으면 `lat/lng/radiusKm`을 생략하고 지역 코드로 검색한다.
- 추천 점수의 내부 계산값은 노출하지 않고 추천 사유 태그만 반환한다.

## 전체 모임 회원 목록

`GET /api/v1/groups/{groupId}/members` — JWT 인증 필요, CSRF 불필요.

폐쇄·삭제·삭제 이력이 없는 모임의 활성 회원만 조회할 수 있으며, 명단에는 활성·미삭제 모임 회원과 미삭제 계정만 `groupMemberId` 오름차순으로 포함한다.
모임장·운영진·요청자도 포함하며 페이지네이션·검색·일정 필터는 제공하지 않는다.
HTTP 200 / `GROUP200_9`, `result.members[]`에는 `groupMemberId`, `memberId`, `nickname`, `role`을 포함한다.
정산 대상에는 계정 ID가 아닌 `groupMemberId`를 전달한다. 비회원은 403, 모임 없음·폐쇄·삭제는 404이다.

## 활동 티어 (#32)

GET 및 PATCH `/api/v1/groups/{groupId}` 성공 응답의 `result.tier`는 읽기 전용 문자열이다.
프런트엔드는 `bronze`, `silver`, `gold`를 각각 브론즈·실버·골드로 표시한다.
초기값은 bronze이며 한국 시간 00시부터 모임별 계산이 커밋될 때 새 값이 보인다.
상세 조회와 수정은 마지막 성공 결과를 읽는다. 산정과 장애 복구 절차는 [티어 배치 운영](../group-tier-operations.md)을 따른다.


## 추천·검색 피드 (#38)

`GET /api/v1/groups?sort=RECOMMENDED` (JWT 쿠키 `moeasy_access_token` 또는 `Authorization: Bearer <access-token>` 필요).
성공은 HTTP 200 / `GROUP200_10`이다. 고정 10장 이내, 내부 점수는 반환하지 않는다.
광고는 모임 ID와 연결되지 않는 독립 배너다.
랜딩 URL은 HTTPS와 유효한 DNS 호스트(국제화 도메인은 punycode), IPv4 또는 대괄호로 감싼 IPv6를 사용한다.
선택 포트는 0~65535 범위이며, 사용자 정보(`user@host`), 공백·제어문자와 역슬래시는 허용하지 않는다.
URL 형식 검사는 외부 사이트의 실제 응답이나 가용성을 보장하지 않는다.

### 요청 필터와 FE 카테고리 대응

| 파라미터 | 기본값 / 계약 |
| --- | --- |
| `sort` | 생략 시 `RECOMMENDED`. 그 외 값은 400 |
| `query` | 생략 시 전체. trim 후 소문자로 정규화, 최대 100자. 모임명·소개·카테고리명·지역명에서 부분 검색. `%`, `_`는 와일드카드가 아닌 문자 |
| `categoryCodes` | 생략 시 전체. 쉼표 또는 반복 파라미터로 여러 실제 BE 코드를 전달. OR 조건. 중복 제거 후 코드 순서 무관 |
| `regionCode` | 생략 시 전체. `GET /api/v1/regions`의 실제 5자리 시·군·구 코드와 정확히 일치. 예: `11680` 강남구, `11110` 종로구 |
| `cursor` | 첫 요청은 생략. 다음 요청은 같은 정렬·필터에 직전 `nextCursor`를 URL 인코딩하여 전달 |

카테고리와 지역 필터, 검색은 후보 분류 **이전**에 모두 AND로 적용한다.
존재하지 않거나 삭제된 카테고리·지역 코드는 400이다. 페이지 크기는 10으로 고정되어 `size` 파라미터를 사용하지 않는다.

[FE ExploreExperience.tsx](https://github.com/DMU-MoEASY/MoEASY-FE/blob/HEAD/src/app/components/ExploreExperience.tsx)를 먼저 확인했다.
현재 FE는 한글 문자열 비교로 탭을 처리하고 BE 코드 대응표를 가지고 있지 않다.
아래 다중 코드 묶음은 **연동 제안안이며 확정 정책이 아니다**. 서버는 탭 별칭을 해석하지 않고 전달된 실제 코드만 적용하므로 매핑 결정과 독립적으로 호출할 수 있다.

| FE 탭 | 전달할 `categoryCodes` 제안 | 근거 / 상태 |
| --- | --- | --- |
| 추천 | 생략 | 전체 후보에 개인화 적용 |
| 운동 | `RUNNING,HIKING` | DB 러닝·등산을 묶는 제안 |
| 스터디 | `STUDY` (+ `DEVELOPMENT` 포함 제안) | STUDY=스터디는 직접 대응. 개발 포함 여부는 FE 확인 필요 |
| 문화 | `READING,PHOTOGRAPHY` | 독서·사진을 묶는 제안 |
| 음식 | `FOOD` | DB 맛집을 음식 탭으로 표시하는 제안 |
| 취미 | `TRAVEL,VOLUNTEERING` | 여행·봉사 배치 제안. 문화/취미 경계는 FE 확인 필요 |
| 게임 | `BOARD_GAME` | 현재 DB에서 제공하는 보드게임 매핑 제안 |

전체 실제 코드: `RUNNING`, `HIKING`, `STUDY`, `READING`, `PHOTOGRAPHY`, `FOOD`, `BOARD_GAME`, `TRAVEL`, `VOLUNTEERING`, `DEVELOPMENT`.
FE의 기존 지역 필터는 도/광역시 이름 `startsWith` 비교다. 이번 API의 `regionCode`는 세부 지역 코드이므로 지역 목록 API를 이용해 선택 UI를 연결해야 한다. 시도 전체 필터는 이번 계약에 없다.

### 적격 후보·점수·추천 사유

- 미삭제 `ACTIVE` 모임 중 정원 미만만 후보로 삼는다. 활성 미삭제 멤버십이 있거나 PENDING 신청이 있거나 KICKED 이력이 있으면 제외한다. KICKED는 멤버십 삭제 여부와 무관하게 제외한다. LEFT·취소/거절 신청은 다른 조건을 통과하면 후보가 된다.
- 현재 인원과 정원은 가입 승인과 공통 SQL 조건인 **멤버십 ACTIVE + deleted_at IS NULL**을 사용한다. 계정의 ACTIVE 여부까지 보는 티어 배치 인원 집계와는 다르다. 배치 로직을 추천 정원으로 사용하지 않는다.
- 관심사 일치 +50 (`INTEREST_MATCH`), 대표 지역 일치 +30 (`REGION_MATCH`), 저장된 silver +5 (`SILVER_TIER`) / gold +10 (`GOLD_TIER`), 최근 활동 +10 (`RECENT_ACTIVITY`). bronze는 가산·태그 없음.
- 첫 요청의 한국 시간 `T`에 `T-30일 < endsAt <= T`인 미삭제·미취소 일정이 있으면 활동 점수를 한 번 가산한다. 여러 일정이나 여러 관심사 때문에 모임 행·점수가 중복되지 않는다.
- 관심사·대표 지역 중 하나라도 일치하면 일반 후보, 모두 불일치하면 낮은 관련성 후보다. 둘 다 비어 있으면 전체 후보가 일반 후보가 된다.
- 각 후보군은 점수 DESC → 생성 시각 DESC → ID DESC. 티어는 #32가 마지막 저장한 `member_group.tier`를 읽으며 요청 중 재계산하지 않는다.

### 페이지 구성·커서

| 후보와 광고 | 반환 조합 |
| --- | --- |
| 일반 >=8, 낮은 관련성 있음, 광고 있음 | 일반8 + 낮은 관련성1 + 광고1 |
| 일반 >=9, 낮은 관련성 없음, 광고 있음 | 일반9 + 광고1 |
| 일반 >=9, 낮은 관련성 있음, 광고 없음 | 일반9 + 낮은 관련성1 |
| 일반 >=10, 낮은 관련성 없음, 광고 없음 | 일반10 |
| 위 조합으로 10장을 못 채움 | 남은 일반만 반환 후 종료 |
| 일반 없음 | 빈 items, hasNext=false, nextCursor=null |

광고는 항상 10번째, 낮은 관련성은 일반 카드 다음이다. 낮은 관련성은 최대 1개이며 일반 후보가 부족한 마지막 페이지를 낮은 관련성으로 채우지 않는다.
활성 광고의 노출 기간은 `startsAt <= T < endsAt`. ID ASC로 A→B→A 순환하며 실제 광고가 반환된 경우만 광고 순번을 증가시킨다. 광고 1개는 반복 노출 가능하다.
`totalGroupCount`는 광고를 제외한 전체 적격·필터 통과 모임 수다. 마지막에 낮은 관련성만 남으면 그 후보는 노출되지 않을 수 있으므로 카드 누적 개수와 다를 수 있다.

커서는 일반/낮은 관련성 각각 **실제로 소비한 마지막 (점수, 생성 시각, ID)**, T, 다음 광고 순번을 포함한다.
회원·정렬·정규화한 필터와 결합하고 HMAC으로 손상을 검증한다. 클라이언트는 해석·수정하지 않는다.
다른 회원·필터·정렬·손상 커서는 기존 공통 400 오류로 처리한다. JWT 서명 키 교체 시 기존 커서도 무효화된다.
동일 데이터와 같은 커서는 같은 카드·순서를 재현한다. 다음 요청은 모임 폐쇄·삭제·가입·대기·강퇴·만석 등 최신 자격을 다시 검사한다.
전체 데이터를 별도로 저장하는 스냅샷은 아니므로 페이지 사이 티어·관심사·모임 내용 등 정렬 요소 자체가 변경되는 경우 전체 순서를 고정하지 않는다. 기준 시각은 최근 활동·광고 기간에 고정 적용된다.

### 카드 계약과 FE 처리

`items[].type`을 discriminator로 사용한다. Swagger의 `Item.oneOf`와 네 가지 응답 예시에도 같은 계약을 제공한다.
200 응답은 `ApiResponse<Feed>` 스키마에 연결된다. 필수 `type`의 값은 `GROUP` 또는 `AD`이며
각각 `GroupCard`, `AdCard`로 명시적으로 매핑한다.

| 유형 | 필드 | FE 처리 |
| --- | --- | --- |
| GROUP | `groupId`, `name`, `description`, `categoryCode`, `categoryName`, `regionCode`, `regionName`, `coverImageUrl`(nullable), `currentMemberCount`, `maxMembers`, `tier`, `recommendationReasons` | 모임 상세로 이동. null 커버는 기본 이미지 사용. 태그를 사용자 문구로 변환 |
| AD | `adId`, `advertiserName`, `title`, `imageUrl`, `landingUrl` | 반드시 **광고** 라벨. 모임 상세가 아닌 HTTPS 외부 링크로 이동 |

상대 `imageUrl`은 **API origin** 기준으로 해석한다 (`new URL(item.imageUrl, apiOrigin)`); FE origin으로 조합하지 않는다.
새 창 링크에는 `target="_blank" rel="noopener noreferrer"`를 사용한다.
FE의 기존 로컬 배열 필터 대신 API 응답을 사용하고 `totalGroupCount`를 모임 개수로 표시한다.
검색/탭/지역을 바꾸면 기존 카드·커서를 초기화한다. 이전 조건의 늦은 응답을 버리고, 다음 페이지 요청 중에는 중복 요청을 막는다.
추가 응답의 `items`를 순서대로 append한다. 키는 `GROUP:${groupId}`, `AD:${adId}:${pageIndex}`처럼 유형과 광고 반복 노출을 구분한다.
`hasNext=false` 또는 `nextCursor=null`이면 요청을 종료한다. 낮은 관련성·광고만을 위한 후속 페이지를 만들지 않는다.

### 응답 예시

아래는 로컬 시연 데이터 기준이다. 설명용 커서 문자열을 요청에 붙이지 말고 **실제 응답의 nextCursor**를 사용한다.

#### 첫 페이지: 일반8 + 발견1 + 광고 A

```json
{
  "isSuccess": true, "code": "GROUP200_10", "message": "추천 모임 피드를 조회했습니다.",
  "result": {
    "items": [
      {"type":"GROUP","groupId":380117,"name":"[추천38] 일반17","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380116,"name":"[추천38] 일반16","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380115,"name":"[추천38] 일반15","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380114,"name":"[추천38] 일반14","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380113,"name":"[추천38] 일반13","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380112,"name":"[추천38] 일반12","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380111,"name":"[추천38] 일반11","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380110,"name":"[추천38] 일반10","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380119,"name":"[추천38] 발견02","description":"로컬 추천 피드 시연","categoryCode":"STUDY","categoryName":"스터디","regionCode":"11110","regionName":"서울특별시 종로구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":[]},
      {"type":"AD","adId":380001,"advertiserName":"MoEASY 시연 A","title":"새로운 모험을 함께해요","imageUrl":"/feed-ads/ad-a.svg","landingUrl":"https://example.com/?banner=A"}
    ],
    "totalGroupCount": 19, "hasNext": true,
    "nextCursor": "서버가 반환한 불투명 커서"
  }
}
```

#### 다음 페이지: 일반8 + 발견1 + 광고 B

```json
{
  "isSuccess": true, "code": "GROUP200_10", "message": "추천 모임 피드를 조회했습니다.",
  "result": {
    "items": [
      {"type":"GROUP","groupId":380109,"name":"[추천38] 일반09","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380108,"name":"[추천38] 일반08","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380107,"name":"[추천38] 일반07","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380106,"name":"[추천38] 일반06","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380105,"name":"[추천38] 일반05","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380104,"name":"[추천38] 일반04","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380103,"name":"[추천38] 일반03","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380102,"name":"[추천38] 일반02","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380118,"name":"[추천38] 발견01","description":"로컬 추천 피드 시연","categoryCode":"STUDY","categoryName":"스터디","regionCode":"11110","regionName":"서울특별시 종로구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":[]},
      {"type":"AD","adId":380002,"advertiserName":"MoEASY 시연 B","title":"이번 주말의 새로운 시작","imageUrl":"/feed-ads/ad-b.svg","landingUrl":"https://example.com/?banner=B"}
    ],
    "totalGroupCount": 19, "hasNext": true,
    "nextCursor": "서버가 반환한 불투명 커서"
  }
}
```

#### 마지막 페이지: 일반1, 종료

```json
{
  "isSuccess": true, "code": "GROUP200_10", "message": "추천 모임 피드를 조회했습니다.",
  "result": {
    "items": [
      {"type":"GROUP","groupId":380101,"name":"[추천38] 일반01","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]}
    ],
    "totalGroupCount": 19, "hasNext": false,
    "nextCursor": null
  }
}
```

#### 광고가 없는 첫 페이지: 일반9 + 발견1

```json
{
  "isSuccess": true, "code": "GROUP200_10", "message": "추천 모임 피드를 조회했습니다.",
  "result": {
    "items": [
      {"type":"GROUP","groupId":380117,"name":"[추천38] 일반17","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380116,"name":"[추천38] 일반16","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380115,"name":"[추천38] 일반15","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380114,"name":"[추천38] 일반14","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380113,"name":"[추천38] 일반13","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380112,"name":"[추천38] 일반12","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380111,"name":"[추천38] 일반11","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380110,"name":"[추천38] 일반10","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380109,"name":"[추천38] 일반09","description":"로컬 추천 피드 시연","categoryCode":"RUNNING","categoryName":"러닝","regionCode":"11680","regionName":"서울특별시 강남구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":["INTEREST_MATCH","REGION_MATCH"]},
      {"type":"GROUP","groupId":380119,"name":"[추천38] 발견02","description":"로컬 추천 피드 시연","categoryCode":"STUDY","categoryName":"스터디","regionCode":"11110","regionName":"서울특별시 종로구","coverImageUrl":null,"currentMemberCount":1,"maxMembers":20,"tier":"bronze","recommendationReasons":[]}
    ],
    "totalGroupCount": 19, "hasNext": true,
    "nextCursor": "서버가 반환한 불투명 커서"
  }
}
```

### 로컬 더미 피드 재현

더미는 `db/dummy/V20261110164908__group_recommendation_demo.sql`에 있으며 **local 프로파일만** Flyway로 적용한다. 운영에는 `db/migration`만 사용한다.
URL 제약 보강은 새 `V20261110234640__strengthen_feed_advertisement_url.sql`로 적용한다.
기존 소재가 새 제약을 위반하면 적용이 실패하며, 데이터를 자동 수정·삭제하지 않는다. 적용 전에 해당 소재의 랜딩 URL을 확인한다.
시연 회원은 `380001`, 운영자는 `380002`. 검색어 `추천38`로 일반17·발견2를 격리한다. 가입·대기·강퇴·만석·보관·폐쇄·삭제 모임 및 시작 전·만료·비활성 광고도 들어 있다.
A/B 이미지는 프로젝트의 `/feed-ads/ad-a.svg`, `/feed-ads/ad-b.svg`이며 외부 이미지 호스팅이나 AWS 호출 없이 제공한다.

기존 로컬 실행 환경변수와 **서버와 같은 JWT_SECRET**을 준비한다. 비밀값은 셸 환경이나 Git에서 제외된 `.env`에서 관리한다.
시연 스크립트는 환경변수만 읽으므로 `.env`만 사용하는 경우 JWT_SECRET을 별도로 셸에 설정한다.

```bash
task docker-up
# 터미널 1: local 프로파일 + 기존 로컬 OAuth/S3 설정, JWT_SECRET을 준비한 상태
task run

# 터미널 2: 서버와 같은 JWT_SECRET 환경변수를 준비한 상태
python3 scripts/local/group-feed-demo.py --output /tmp/moeasy-feed-page1.json
python3 scripts/local/group-feed-demo.py --after /tmp/moeasy-feed-page1.json --output /tmp/moeasy-feed-page2.json
python3 scripts/local/group-feed-demo.py --after /tmp/moeasy-feed-page2.json --output /tmp/moeasy-feed-page3.json
```

서버 포트가 다르면 `--base-url http://127.0.0.1:8081`을 추가한다. 스크립트는 로컬 주소만 허용하며 인증 쿠키나 비밀값을 출력하지 않는다.
첫 응답은 10장(일반8+발견1+A), 다음은 10장(일반8+발견1+B), 마지막은 일반1장이다. 모두 totalGroupCount=19이며 마지막 hasNext=false·nextCursor=null이다.
별도 로컬 광고를 추가하면 공통 광고 풀에 포함되므로 정확한 A/B 재현은 더미만 있는 DB에서 수행한다.

직접 호출은 로그인으로 발급받은 쿠키 파일을 사용할 수 있다. 아래 첫 요청의 실제 nextCursor를 두 번째 명령에 전달한다.

```bash
curl -b /tmp/moeasy-cookies.txt --get 'http://localhost:8080/api/v1/groups' \
  --data-urlencode 'sort=RECOMMENDED' --data-urlencode 'query=추천38'
curl -b /tmp/moeasy-cookies.txt --get 'http://localhost:8080/api/v1/groups' \
  --data-urlencode 'sort=RECOMMENDED' --data-urlencode 'query=추천38' \
  --data-urlencode "cursor=$NEXT_CURSOR"
```

추천 피드 통합 테스트는 실제 MySQL·Redis, JWT 쿠키와 보안 필터, MockMvc HTTP 처리를 사용한다. 테스트 데이터는 트랜잭션 rollback으로 격리한다.
일반17·발견2·광고2의 모임 ID 중복/누락·총19·종료 커서, 배점·정렬·사유, 일정/광고 경계, 제외 대상, 필터, 잘못된 커서, 재요청, 조회 중 자격 변경을 검증한다. 더미 SQL 자체의 3페이지 API 재현도 별도 테스트한다.
FE 통합 후 실제 카드 렌더링·광고 라벨·클릭 시 외부 링크 이동은 FE에서 확인해야 한다. 실제 광고 네트워크·관리 화면·노출/클릭 집계·과금은 이번 범위에 없다.
