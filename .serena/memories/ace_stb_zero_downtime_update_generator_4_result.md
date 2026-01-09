# Generator Iteration 4: Final Architecture & Implementation Roadmap

## Playbook 참조
- 전체 17 bullets 반영
- STR-001~008, CON-001~005, FAIL-001~006

## 최종 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Application Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
│  │ MainActivity │  │  Settings   │  │   EPG UI    │                 │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                 │
│         │                │                │                         │
│         └────────────────┴────────────────┘                         │
│                          │                                          │
│                          ▼                                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    UpdateManager (Orchestrator)              │   │
│  │  ┌─────────────────────────────────────────────────────┐    │   │
│  │  │ State: IDLE→CHECK→DOWNLOAD→VERIFY→STAGE→WAIT→SWAP  │    │   │
│  │  └─────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
├─────────────────────────────┼───────────────────────────────────────┤
│                        Core Layer                                   │
│         ┌───────────────────┼───────────────────────┐              │
│         │                   │                       │              │
│         ▼                   ▼                       ▼              │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────────┐     │
│  │ SlotManager  │   │DownloadMgr  │   │HotSwapPluginLoader │     │
│  │  (A/B Slot)  │   │ (Resume,    │   │  (Pre-warming,     │     │
│  │              │   │  Delta)     │   │   Swap, Rollback)  │     │
│  └──────┬───────┘   └──────┬──────┘   └─────────┬──────────┘     │
│         │                  │                    │                 │
│         └──────────────────┴────────────────────┘                 │
│                            │                                       │
│         ┌──────────────────┼──────────────────────┐               │
│         │                  │                      │               │
│         ▼                  ▼                      ▼               │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐      │
│  │HealthChecker│   │RollbackMgr  │   │StartupWatchdog   │      │
│  │ (Validation)│   │ (Auto/Manual)│   │ (Crash Detection)│      │
│  └──────────────┘   └──────────────┘   └──────────────────┘      │
│                                                                    │
├────────────────────────────────────────────────────────────────────┤
│                      Plugin Layer                                  │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                 ZeroDowntimePlugin Interface                │   │
│  │  • initialize() / onDestroy()                               │   │
│  │  • getStateSnapshot() / restoreState()                      │   │
│  │  • prepareForSwap() / canSafelySwap()                       │   │
│  │  • createView()                                             │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  ┌──────────────┐         ┌──────────────┐                        │
│  │   Slot A     │ ◄─────► │   Slot B     │                        │
│  │ (Active)     │         │ (Standby)    │                        │
│  │ plugin_v1.apk│         │ plugin_v2.apk│                        │
│  └──────────────┘         └──────────────┘                        │
│                                                                    │
├────────────────────────────────────────────────────────────────────┤
│                    Persistence Layer                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │ SlotState.json  │  │ PluginState.json│  │ Watchdog.prefs  │   │
│  │ (Active slot,   │  │ (Serialized     │  │ (Startup count, │   │
│  │  versions)      │  │  plugin state)  │  │  timestamps)    │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

## 구현 로드맵

### Phase 1: Foundation (2주)
**목표**: 기본 A/B 슬롯 및 핫스왑 인프라

| 작업 | 우선순위 | 예상 일수 |
|------|---------|----------|
| SlotManager 구현 | P0 | 2일 |
| HotSwapPluginLoader 구현 | P0 | 3일 |
| ZeroDowntimePlugin 인터페이스 정의 | P0 | 1일 |
| 기본 Unit Tests | P0 | 2일 |
| 기존 PluginLoader 마이그레이션 | P1 | 2일 |

**Deliverable**: 수동 슬롯 전환 가능

### Phase 2: Automation (2주)
**목표**: 자동 업데이트 플로우

| 작업 | 우선순위 | 예상 일수 |
|------|---------|----------|
| UpdateManager 구현 | P0 | 3일 |
| DownloadManager (Resume) | P1 | 2일 |
| HealthChecker 구현 | P0 | 2일 |
| 상태 마이그레이션 로직 | P1 | 2일 |
| Integration Tests | P0 | 1일 |

**Deliverable**: 자동 다운로드/설치/검증 플로우

### Phase 3: Resilience (2주)
**목표**: 복구 및 안정성

| 작업 | 우선순위 | 예상 일수 |
|------|---------|----------|
| RollbackManager 구현 | P0 | 2일 |
| StartupWatchdog 구현 | P0 | 2일 |
| Factory Reset 메커니즘 | P1 | 1일 |
| 에러 복구 시나리오 테스트 | P0 | 3일 |
| E2E Tests on STB | P0 | 2일 |

**Deliverable**: 자동 롤백 및 크래시 복구

### Phase 4: Optimization (1주)
**목표**: STB 특화 최적화

| 작업 | 우선순위 | 예상 일수 |
|------|---------|----------|
| Delta Update 구현 | P2 | 2일 |
| Pre-warming 메모리 최적화 | P2 | 1일 |
| Swap Timing (광고 중) 연동 | P1 | 1일 |
| 모니터링/로깅 강화 | P2 | 1일 |

**Deliverable**: 프로덕션 레디

## 파일 구조 제안

```
app/src/main/java/com/example/dexloadingtest/
├── plugin/
│   ├── PluginInterface.kt          # 기존 유지
│   ├── ZeroDowntimePlugin.kt       # 확장 인터페이스 (NEW)
│   ├── PluginLoader.kt             # 기존 유지 (하위 호환)
│   └── HotSwapPluginLoader.kt      # 핫스왑 로더 (NEW)
├── update/
│   ├── UpdateManager.kt            # 업데이트 오케스트레이터 (NEW)
│   ├── UpdateState.kt              # 상태 머신 정의 (NEW)
│   ├── DownloadManager.kt          # 다운로드 관리 (NEW)
│   └── VersionInfo.kt              # 버전 메타데이터 (NEW)
├── slot/
│   ├── SlotManager.kt              # A/B 슬롯 관리 (NEW)
│   └── SlotState.kt                # 슬롯 상태 정의 (NEW)
├── recovery/
│   ├── HealthChecker.kt            # 헬스체크 (NEW)
│   ├── RollbackManager.kt          # 롤백 관리 (NEW)
│   └── StartupWatchdog.kt          # 크래시 감지 (NEW)
└── state/
    ├── StateSerializer.kt          # 상태 직렬화 (NEW)
    └── StateMigrator.kt            # 상태 마이그레이션 (NEW)
```

## STB 특화 고려사항

### 1. 메모리 제약 (512MB~2GB)
- Pre-warming은 availableMemory > 256MB일 때만 활성화
- 대기 플러그인 로드 실패 시 on-demand로 전환
- GC 유도 후 재시도 로직

### 2. 스토리지 제약 (4GB~16GB)
- 최대 2개 버전만 유지 (Active + Standby)
- 업데이트 전 스토리지 체크 (APK 크기 * 2 + 여유분)
- 실패한 다운로드 자동 정리

### 3. 네트워크 불안정
- 청크 단위 다운로드 (1MB 청크)
- 각 청크 해시 검증
- Resume 다운로드 필수
- 오프라인 모드 지원 (로컬 APK 대기)

### 4. 24/7 운영
- 업데이트 시간대 설정 (새벽 3-5시)
- 사용자 활동 감지 (시청 중이면 연기)
- 강제 업데이트는 보안 패치에만

## 추론 기록
- Phase 1~3이 핵심, Phase 4는 선택적 최적화
- 기존 PluginLoader와 호환성 유지하여 점진적 마이그레이션 가능
- E2E 테스트는 실제 STB 기기에서 필수
