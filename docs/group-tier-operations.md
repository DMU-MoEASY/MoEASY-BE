# 모임 티어 운영

매일 한국 시간 00시에 시작하며 10분 이내 완료가 목표다. 매분 최신 기준일의 미처리·실패·소유권 만료 작업을 확인한다.
기준일 D의 활동 범위는 (D−56일 00시, D 00시]이다. ACTIVE·ARCHIVED 모임의 취소·삭제되지 않은 종료 일정은 각각 활동 1회다.
종료 명단이 5명 이상이고 사전 신청을 복원할 수 있으며 수동 집계가 완료된 일정만 평가한다.
완료 후 인정 참석자가 없으면 0명으로 합산한다. 현장 참석을 제외하고 사전 ATTENDING이면서 PRESENT·LATE인 고유 멤버를 센다.
활동 점수는 min(A,8) × 7.5, 참여 점수는 40 × (인정 참석자 합 / 종료 인원 합)이다.
입력 고정 시점 활성 인원이 5명 이상이면 평가 8회·80점부터 Gold, 4회·50점부터 Silver, 나머지는 Bronze다.
비율은 중간 반올림하지 않는다. 저장 점수는 표시를 위해 소수점 8자리로 반올림한다.

## 이행 및 자격 변경

Flyway 이행 전에는 쓰기를 중지하고 마이그레이션과 애플리케이션 배포를 마친 후 재개한다.
attendance_cutover.established_at은 한국 시간 T다. 기존 활성 자격을 T부터 열고, 기존 RSVP는 재확인 시 현재 자격 구간과 연결한다.
T 이전 시작 일정은 활동만 반영한다. 기존 QR CLOSED는 수동 집계와 독립적으로 유지한다.
자격 구간은 [시작, 종료)이다. 모임 생성·승인은 같은 트랜잭션에서 openPeriod를 호출한다.
향후 탈퇴·강퇴·회원 비활성화·소프트 삭제는 해당 모임을 먼저 잠근 뒤 같은 트랜잭션에서 closePeriod를 호출해야 한다.
회원 단위 변경은 모든 관련 모임을 ID 순으로 잠근다. 복구·재가입은 새 구간을 열며 과거 구간을 수정하지 않는다.
멤버 상태만 직접 바꾸는 SQL은 이 계약을 우회하므로 사용하지 않는다.

## 지연 및 실패 확인

group_tier_job은 (basis,group_id)마다 한 행이다. frozen_at이 있으면 재시도는 저장된 입력을 사용한다.
작업 소유권은 1분 동안 유효하며 새 작업자가 회수한 뒤에는 이전 소유자가 반영할 수 없다.
장애 원인을 해결한 후 다음 분의 재시도를 확인한다. 서버가 여러 날 중지되면 최신 기준일을 처리하고 이전 누락 작업을 SKIPPED로 기록한다.
일정 삭제·재개방·인원 변동은 다음 입력 고정 때 반영된다. 폐쇄·삭제 모임 및 이미 더 최근 기준일이 반영된 작업도 SKIPPED다.

아래 쿼리에서 :basis는 한국 날짜, :group_id는 대상 모임 ID로 바꾼다.

```sql
SELECT state, COUNT(*) FROM group_tier_job WHERE basis=:basis GROUP BY state;
SELECT group_id, attempts, failure, lease_until, frozen_at
FROM group_tier_job WHERE basis=:basis AND state IN ('FAILED','RUNNING') ORDER BY group_id;
SELECT basis, frozen_at, activities, evaluated, numerator, denominator, active_members,
       score, result, policy_version FROM group_tier_job WHERE group_id=:group_id ORDER BY basis DESC;
```

실패 항목을 다음 점검에서 즉시 처리하도록 예약하려면 FAILED 행의 재시도 시각만 앞당긴다.

```sql
UPDATE group_tier_job SET lease_until=UTC_TIMESTAMP()+INTERVAL 9 HOUR
WHERE basis=:basis AND group_id=:group_id AND state='FAILED';
```

DONE 행과 고정 입력은 유지한다. 로그의 basis·group으로 오류를 찾고 processed·elapsedMs로 처리량과 소요 시간을 확인한다.
