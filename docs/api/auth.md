# 인증과 Swagger API 테스트

## 구현 상태

| 항목 | 상태 | 동작 |
| --- | --- | --- |
| JWT 쿠키 인증 | 구현됨 | `moeasy_access_token` 쿠키의 액세스 JWT 검증 |
| 로그인·토큰 재발급 | 구현됨 | 액세스·리프레시 토큰을 HttpOnly 쿠키로 전달 |
| CSRF 검증 | 구현됨 | 안전한 HTTP 메서드를 제외한 요청에 CSRF 쿠키·헤더 검증 |
| Authorization Bearer 인증 | 구현됨 | 헤더의 액세스 JWT 우선 검증, 헤더가 없으면 기존 쿠키 사용 |
| Swagger Authorize JWT 입력 | 구현됨 | HTTP Bearer 인증 스키마와 보호 API 보안 선언 |
| CORS Authorization 헤더 허용 | 구현됨 | 기존 허용 출처·credentials 정책을 유지하며 Authorization 헤더 허용 |

Swagger의 Authorize에 액세스 JWT 원문을 입력하거나, API 서버 도메인에 저장된 유효한 인증 쿠키로 인증할 수 있다.

## 현재 쿠키 인증

- `moeasy_access_token`: 일반 API 인증에 사용하는 액세스 JWT. 경로는 `/api/v1`, 기본 유효기간은 5분이다.
- `moeasy_refresh_token`: 토큰 재발급·로그아웃에 사용하는 리프레시 JWT. 경로는 `/api/v1/auth`, 기본 유효기간은 14일이다.
- 두 토큰은 로그인·재발급 응답의 `Set-Cookie`로 전달하며 HttpOnly 속성을 사용한다. Secure·SameSite는 서버의 쿠키 설정을 따른다.
- 보호 API는 JWT 검증 후 토큰의 회원 ID를 principal로 사용한다. 모임 소속·운영진 역할 등 도메인 권한은 각 API에서 추가 검증한다.
- 브라우저와 Postman의 쿠키 저장소는 별개다. 프론트엔드 프록시를 통해 프론트 도메인에 저장된 쿠키는 백엔드 Swagger 도메인에 자동으로 공유되지 않는다.

## Bearer 인증

보호 API는 다음 형태의 액세스 JWT도 받는다.

```http
Authorization: Bearer <access-token>
```

| 요청의 인증 정보 | 처리 |
| --- | --- |
| Authorization 헤더 없음, 액세스 쿠키 있음 | 기존 쿠키의 JWT 검증 |
| Authorization 헤더 없음, 액세스 쿠키 없음 | 익명 요청으로 처리하며 보호 API는 기존 401 응답 |
| 유효한 Bearer 헤더 있음 | 헤더의 액세스 JWT로 인증 |
| Bearer 헤더와 액세스 쿠키가 함께 있음 | 헤더 우선. 쿠키와 다른 회원의 토큰이면 헤더의 회원으로 인증 |
| Authorization 헤더가 빈 값·잘못된 형식이거나 JWT 검증 실패 | 기존 401 응답. 정상 쿠키가 있어도 대체 인증하지 않음 |

- 인증 스킴 `Bearer`는 대소문자를 구분하지 않는다. 헤더 값에는 액세스 JWT 하나를 전달한다. Authorization 헤더를 여러 개 전달하면 401로 거부한다. 쿼리나 요청 본문의 `access_token`은 인증에 사용하지 않는다.
- 헤더와 쿠키 모두 기존 JWT 검증을 재사용한다. 서명·발급자·만료와 액세스 토큰 종류, 양수 회원 ID 검증을 유지한다. 리프레시 JWT로 일반 API에 접근할 수 없다.
- Bearer 지원은 Swagger와 일반 HTTP 클라이언트에 동일하게 적용한다. Swagger 요청이라는 이유로 인증·권한 검증을 생략하지 않는다.
- 로그인·CSRF 발급·OAuth state 발급·토큰 재발급·로그아웃 등 기존 공개 경로는 공개 상태를 유지한다. 공개 경로의 기존 인증 필터 제외 정책도 유지한다.
- Authorize 입력은 토큰을 새로 발급하지 않는다. 기존 로그인 등으로 발급받은 유효한 액세스 JWT를 사용한다.
- 로그인·재발급의 응답 계약은 변경하지 않는다. 재발급·로그아웃은 계속 리프레시 쿠키를 사용하며 Authorize의 액세스 JWT로 대체하지 않는다.
- 인증 실패는 기존 `401 / AUTH401_1` 형식을 사용한다. 보안 필터 실행 순서에 따라 CSRF 검증 실패가 먼저 처리될 수 있다.

## CSRF와 CORS

- CSRF 검증은 인증 정보의 전달 위치와 관계없이 적용한다. Bearer를 사용해도 CSRF 검증을 생략하지 않는다.
- `GET`, `HEAD`, `OPTIONS`, `TRACE`는 기본 CSRF 검증 대상에서 제외한다. `POST`, `PUT`, `PATCH`, `DELETE`는 기존 CSRF 쿠키·헤더가 필요하다. 저장하지 않는 POST 미리보기도 포함한다.
- 먼저 `GET /api/v1/auth/csrf`를 호출한다. 서버는 `XSRF-TOKEN` 쿠키를 설정하며 응답 본문의 `result.token`과 `result.headerName`을 제공한다.
- 이후 검증 대상 요청에는 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더를 함께 보낸다. 헤더 값은 CSRF 발급 응답의 `result.token`을 사용한다. JWT를 CSRF 헤더에 넣지 않는다.
- CSRF 누락·불일치는 기존 `403 / AUTH403_1` 응답이다.
- 교차 출처의 쿠키 요청에는 `credentials: 'include'` 또는 `withCredentials: true`가 필요하다. 프론트 JavaScript에서 백엔드 쿠키를 직접 읽는 방식에 의존하지 않는다.
- CORS는 기존 허용 출처와 credentials 정책을 유지하며 `Authorization` 헤더를 허용한다. 같은 출처의 Swagger 요청과 교차 출처 프론트 요청을 구분한다.

## Swagger 테스트 절차

1. 테스트할 회원의 유효한 액세스 JWT를 준비한다.
2. Swagger의 `Authorize`에서 Bearer 인증 입력란에 JWT 원문을 입력한다. `Bearer ` 접두사는 Swagger가 붙이므로 입력하지 않는다.
3. 보호 API 요청의 `Authorization: Bearer ...` 헤더를 확인한다. GET 조회에는 CSRF 발급이 필요하지 않다.
4. 상태 변경 API 테스트 전, 같은 Swagger에서 `GET /api/v1/auth/csrf`를 호출하고 `result.token`을 복사한다.
5. 대상 API의 `X-XSRF-TOKEN` 헤더에 CSRF 값을 입력한다. API 서버의 CSRF 쿠키도 함께 전송되어야 한다.
6. 토큰 만료 시 새 액세스 JWT로 Authorize 값을 갱신한다. 입력값을 저장소·문서·로그에 기록하지 않는다.

OpenAPI는 HTTP Bearer/JWT 인증 스키마를 선언하고 보호 API에 적용한다. 공개 API에는 JWT 입력을 요구하지 않는다. API별 CSRF 헤더 명세와 기존 성공·오류 응답 계약은 유지한다.

## 검증 범위

기존 JWT 필터·그룹 보안 테스트와 공통 OpenAPI 보안 통합 테스트에서 아래 동작을 검증한다.

- 정상 Bearer 인증과 기존 쿠키 인증, 헤더와 쿠키가 함께 있을 때의 헤더 우선순위.
- 잘못된 헤더가 정상 쿠키로 대체되지 않는지와 기존 JWT 검증 실패 처리.
- Bearer 인증에서도 CSRF 없는 상태 변경 요청 거부 및 정상 CSRF 요청의 연결.
- CORS의 Authorization 허용과 OpenAPI의 보호 API·공개 API 보안 선언.
