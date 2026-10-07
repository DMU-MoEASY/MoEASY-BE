# Group API 명세

## 범위와 공통 계약

Group API의 공통 접두사는 `/api/v1`이다. 아래 표의 `/groups` 경로에는 모두 이 접두사가 적용된다. Issue #14는 아래 네 API만 구현한다. 목록·검색·가입·초대 및 참여/미참여 조회는 [후속 범위](#후속-범위)에 보존한다.

- Group API의 식별자 `groupId`는 `member_group.id`에 대응하는 양의 정수다.
- 네 API는 모두 Issue #15의 JWT로 인증된 회원만 호출할 수 있다. 상세 조회에는 Group 멤버십을 요구하지 않는다. 수정과 폐쇄는 해당 Group의 활성 `OWNER` 멤버십을 요구한다.
- 명세의 기존 `STAFF`는 DB 역할 `MANAGER`를 뜻한다. `MANAGER` 역할 Enum은 정의하지만 이번 범위에서 권한을 부여하거나 사용하는 API는 없다.
- 정상·오류 응답은 공통 `ApiResponse<T>` 형식을 따른다. 아래 Group 코드는 Issue #14의 구현 계약이며 아직 Java Enum에 추가되지 않았다.

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

`name`, `description`, 숫자 `categoryId`, `regionCode`, `placeName`, `address`, `maxMembers`, `joinPolicy`는 필수다. 길이·허용값은 `member_group`의 컬럼과 제약을 따른다. `categoryId`는 존재하며 삭제되지 않은 `category.id`여야 한다. `latitude`와 `longitude`는 둘 다 생략하거나 둘 다 제공한다. 값의 범위는 각각 -90~90, -180~180이다. `coverImageKey`는 선택 사항이다.

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
- 좌표는 `latitude`와 `longitude`를 함께 제공한다. 두 숫자는 함께 갱신하고, 두 필드 모두 `null`이면 좌표를 제거한다. 한쪽만 제공하거나 한쪽만 `null`이면 HTTP 400이다.
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
| 요청 형식·필수값·PATCH 빈 객체 또는 좌표 쌍 오류 | 400 | `VALID400_1` 또는 `COMMON400_1` | 공통 검증 오류 응답 |
| Group이 없거나 폐쇄됨 | 404 | `GROUP404_1` | 모임을 찾을 수 없습니다. |
| Category가 없거나 삭제됨 | 404 | `GROUP404_2` | 카테고리를 찾을 수 없습니다. |
| 수정·폐쇄 요청자가 OWNER가 아님 | 403 | `AUTH403_1` | 요청이 거부되었습니다. |
| JWT가 없거나 유효하지 않음 | 401 | Issue #15 인증 오류 코드 | Issue #15의 필터 응답 계약을 따른다. |

Group 전용 오류 코드는 구현 시 `GroupErrorCode`에 추가한다. 인증 오류는 Issue #15가 제공하는 필터·코드를 재사용한다.

## 커버 이미지

요청 필드 `coverImageKey`는 S3 객체 **키**이며 파일 ID나 URL이 아니다. 비어 있지 않은 최대 500자 문자열이어야 한다. 키는 별도 커버 이미지 업로드 발급 흐름에서 인증 회원에게 발급받아야 하며, Group CRUD 범위에는 이 키와 업로드 URL을 발급하는 HTTP API가 포함되지 않는다. 발급 키는 `group-covers/{memberId}/{UUID}` 형식이다. 비공개 버킷에 객체가 존재하고 해당 회원에게 발급된 키인지 저장 전에 확인한다. Group 내부 서비스는 발급 이력의 회원 ID와 S3 객체 존재·Content-Type을 검증한다.

응답에는 키 대신 `S3StorageService.createPresignedGetUrl`로 만든 `coverImageUrl`을 사용한다. URL은 설정된 유효 시간이 지나면 다시 조회해 발급받아야 한다. 이미지가 없으면 `null`이다. 비공개 S3 접근·서명 요청에는 요청과 데이터 전송 비용이 발생할 수 있다.

## 후속 범위

아래 항목은 이동 전 Group 명세의 내용을 보존한 것이다. Issue #14에서 구현하거나 이번 오류·권한 계약을 적용하지 않는다. 각 기능을 구현할 때 경로·권한·상태·응답을 재확정한다. 폐쇄 Group의 콘텐츠 비노출도 해당 콘텐츠 기능에서 적용한다.

| Method | Endpoint | 기능 | 기존 명세의 권한 |
| --- | --- | --- | --- |
| GET | /groups | 키워드·카테고리·지역·거리 기반 검색 | Public/USER |
| GET | /users/me/groups | 내 참여 모임 조회 | USER |
| POST | /groups/{groupId}/join-requests | 가입 신청 | USER |
| GET | /groups/{groupId}/join-requests/me | 내 가입 신청 상태 | USER |
| DELETE | /groups/{groupId}/join-requests/me | 대기 중 가입 신청 취소 | 신청자 |
| POST | /groups/{groupId}/invite-links | 초대 링크·코드 생성 | GROUP_STAFF |
| GET | /group-invites/{inviteCode} | 초대 링크 유효성·모임 요약 조회 | Public |
| POST | /group-invites/{inviteCode}/accept | 초대 수락 또는 가입 신청 | USER |
| DELETE | /groups/{groupId}/invite-links/{inviteId} | 초대 링크 폐기 | GROUP_STAFF |

### 기존 검색 메모

`GET /groups?q=러닝&category=SPORTS&regionCode=11680&lat=37.5665&lng=126.9780&radiusKm=5&sort=DISTANCE&cursor=...&size=20`

- `sort`: RECOMMENDED, DISTANCE, NEWEST, MEMBER_COUNT
- 위치 권한이 없으면 `lat/lng/radiusKm`을 생략하고 지역 코드로 검색한다.
- 추천 점수의 내부 계산값은 노출하지 않고 추천 사유 태그만 반환한다.
