# ACE Playbook: stb_zero_downtime_update

## metadata
- task_id: stb_zero_downtime_update
- project: DexLoading (Android Dynamic Dex Loading)
- created_at: 2026-01-09
- updated_at: 2026-01-09
- total_bullets: 21
- iterations: 4
- status: COMPLETED
- description: Set-top box zero-downtime update architecture requirements analysis

## strategies

### S-001: Quiescent State Detection
핫 스왑 시 진행 중 작업을 추적하기 위해 Reference Counting 패턴 사용
- acquire()/release() 메서드로 활성 참조 추적
- isQuiescent() == true 일 때만 스왑 진행
- 타임아웃 + 강제 스왑 옵션 제공
- 적용: PluginWrapper 인터페이스로 구현

### S-002: Version-Agnostic State
상태 마이그레이션 복잡도를 줄이기 위해 버전 독립적 상태 표현 사용
- JSON + JSON Schema 기반
- 필드 추가: 하위 호환 (구버전이 새 필드 무시)
- 필드 제거: @Deprecated 마킹 후 N+2 버전에서 제거
- 타입 변경: 금지! 새 필드로 대체
- 적용: StateRepository에 versioned schema 적용

### S-003: Lean A/B with Remote Fallback
스토리지 제약을 극복하면서 롤백 기능 유지
- 현재 버전만 로컬 전체 보관
- 이전 버전은 URL + SHA256 해시만 저장
- 긴급 롤백용 미니멀 버전(기본 기능만) 로컬 보관
- 롤백 시: 미니멀 즉시 적용 → 정식 버전 백그라운드 다운로드

### S-004: Resource Management with withPlugin Pattern (NEW)
플러그인 참조 관리에 RAII/AutoCloseable 패턴 적용
- 수동 acquire/release 대신 withPlugin {} 헬퍼 사용
- finally 블록에서 자동 release 보장
- 메모리 누수 방지

### S-005: Transactional State Migration (NEW)
상태 마이그레이션에 트랜잭션 패턴 적용
- 마이그레이션 전 deepCopy 스냅샷 생성
- 부분 실패 시 스냅샷으로 롤백
- 데이터 무결성 보장

### S-006: Timeout Everything (NEW)
모든 비동기 작업에 타임아웃 설정 필수
- 네트워크 요청: 30초
- 헬스 체크: 10초
- Quiescent 대기: 30초
- withTimeoutOrNull {} 래퍼 사용

### S-007: Multi-layer Recovery
복구 메커니즘을 계층화하여 다층 방어 구축
- Layer 1: HealthChecker (업데이트 직후 검증)
- Layer 2: RollbackManager (자동 롤백)
- Layer 3: StartupWatchdog (크래시 감지)
- Layer 4: Factory Reset (최후의 수단)
- 각 층은 독립적으로 동작, 하위 층 실패 시 상위 층 동작

### S-008: Startup Watchdog Pattern
앱 시작 시간 추적으로 크래시 루프 자동 감지
- 시작 시간 기록, 빠른 재시작(10초 이내)은 크래시로 판단
- N회 연속 크래시 시 자동 롤백
- 정상 실행 확인 후(30초) 카운터 리셋
- 사용자 개입 없이 STB 안정성 유지

### S-009: Phased Rollout Strategy
구현을 4단계로 나누어 점진적 가치 전달
- Phase 1: Foundation (기본 A/B 슬롯, 수동 전환)
- Phase 2: Automation (자동 업데이트 플로우)
- Phase 3: Resilience (복구 및 안정성)
- Phase 4: Optimization (Delta, Pre-warming)
- 각 Phase 완료 후 프로덕션 배포 가능

## concepts

### C-001: Zero-Downtime Update 핵심 요소
무중단 업데이트를 위한 4가지 필수 요소:
1. Hot Swap: 서비스 중단 없는 코드 교체
2. State Migration: 업데이트 전후 상태 보존
3. A/B Slot: 롤백을 위한 버전 관리
4. Health Check: 업데이트 성공 여부 검증

### C-002: 우선순위 기준
P0 (Critical): 무중단 운영 필수 - atomic swap, A/B slot, auto rollback
P1 (High): 안정성 - graceful transition, state serialization, signature verification
P2 (Medium): 효율성 - background download, delta update
P3 (Low): 운영 편의 - version compatibility, multi-version retention

### C-003: Rich Error Context (NEW)
에러 상태에 포함해야 할 정보:
- stage: 실패 단계 (DOWNLOAD, INSTALL, HEALTH_CHECK, STATE_MIGRATE)
- recoveryAction: 가능한 복구 액션 (RETRY, ROLLBACK, FACTORY_RESET)
- metrics: 디버깅용 메트릭
- stack trace

### C-004: Rollback Budget
롤백 횟수와 빈도 제한으로 무한 루프 방지
- MAX_ROLLBACK_COUNT = 3 (기본값)
- ROLLBACK_COOLDOWN = 60초
- 예산 소진 시 수동 개입 요청 또는 Factory Reset
- 성공적인 업데이트 후 카운터 리셋

### C-005: Recovery Cost Hierarchy
복구 비용을 고려한 단계적 복구 선택
```
Low:  Slot Swap (< 1초, 무비용)
Med:  Re-download (분~시간, 네트워크 비용)
High: Factory Reset (설정 초기화, UX 비용)
```
- 항상 낮은 비용의 복구부터 시도

### C-006: Layered Architecture for Update
무중단 업데이트를 위한 4계층 아키텍처
```
Application (UI, 사용자 상호작용)
    ↓
Core (UpdateManager, SlotManager, Recovery)
    ↓
Plugin (ZeroDowntimePlugin Interface)
    ↓
Persistence (State, Config, Watchdog)
```
- 각 계층은 하위 계층에만 의존
- 테스트 용이성 및 유지보수성 향상

### C-007: STB Resource Constraints
STB 특성에 맞는 리소스 제약 수치
- 메모리: Pre-warming 임계치 256MB (그 이하면 on-demand)
- 스토리지: 최대 2버전 유지 (Active + Standby)
- 네트워크: 청크 1MB + Resume 필수
- 업데이트 시간: 새벽 3-5시 (사용자 활동 최소)

## failure_modes

### FM-001: 업데이트 중 전원 차단
영향: 부팅 불가
대응: 부트로더 수준 복구 모드 필수

### FM-002: 상태 마이그레이션 실패
영향: 사용자 설정 손실
대응: 트랜잭션 롤백 → 상태 초기화 + 사용자 알림

### FM-003: 헬스체크 무한 실패
영향: 무한 롤백 루프
대응: 3회 실패 후 공장 초기화 모드 진입

### FM-004: Quiescent 타임아웃 (NEW)
영향: 업데이트 지연 또는 강제 스왑으로 인한 크래시
대응: 타임아웃 후 강제 스왑 + 경고 로그 + 모니터링 알림

### FM-005: Rollback Loop
영향: 새 버전과 이전 버전 모두 실패, 무한 롤백
원인: 공통 의존성 문제, 손상된 저장소, 하드웨어 이슈
대응: MAX_ROLLBACK_COUNT 도달 시 Factory Reset 트리거
예방: 각 버전 독립 검증, 공통 의존성 버전 고정

### FM-006: Watchdog False Positive
영향: 정상 재시작을 크래시로 오인하여 불필요한 롤백
원인: 사용자 빠른 앱 전환, Force Stop, 시스템 리부팅
대응: CRASH_THRESHOLD 조정 (10초 → 5초), 명시적 종료 플래그 체크
예방: onStop()에서 정상 종료 플래그 설정

### FM-007: Resource ID Collision
영향: 잘못된 리소스 로드, UI 깨짐, 크래시
원인: Host(0x7f)와 Plugin(0x71)의 R.id 충돌
대응: Plugin 전용 리소스 ID 범위 할당 (AAPT2 --package-id)
예방: 빌드 시 패키지 ID 명시적 설정, 리소스 접두사 규칙
