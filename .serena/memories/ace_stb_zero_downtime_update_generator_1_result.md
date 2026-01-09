# Generator Iteration 1: Zero-Downtime Update Requirements Analysis

## 분석 대상
- 현재 코드베이스: Android Dynamic DEX Loading Test Project
- 타겟 플랫폼: Set-top Box (STB)

## 현재 구현 상태

### 강점
1. **DexClassLoader 기반 동적 로딩** - 런타임 플러그인 로딩 가능
2. **리소스 분리** - Plugin용 별도 Resources/AssetManager
3. **플러그인 라이프사이클** - load/unload 관리
4. **Android 14+ 대응** - read-only 보안 요구사항 처리

### Zero-Downtime을 위한 Gap 분석

| Gap | 현재 상태 | 필요 요소 |
|-----|----------|-----------|
| Hot-Swap | unload→load 순차 | 병렬 준비 + 원자적 전환 |
| Version Management | 없음 | 버전 비교, 롤백 |
| Download Manager | assets에서만 로드 | 네트워크 다운로드, 캐싱 |
| State Migration | 상태 손실 | 상태 직렬화/복원 |
| Integrity Verification | 없음 | 서명/해시 검증 |
| Graceful Handover | 없음 | 진행 중 작업 인계 |
| Background Preparation | 없음 | 백그라운드 사전 로딩 |
| Health Checking | 없음 | 로드 후 검증 |

## Zero-Downtime Update 핵심 요구사항

### 1. STB 특수 요구사항
- **24/7 운영**: 셋톱박스는 항상 켜져 있음
- **사용자 경험**: 시청 중 업데이트 감지 불가해야 함
- **리소스 제약**: 메모리/스토리지 한정
- **네트워크 불안정**: 다운로드 재개 필요
- **복구 가능성**: 업데이트 실패 시 자동 롤백

### 2. 아키텍처 요구사항

#### 2.1 Dual-Slot (A/B) 메커니즘
```
Slot A: 현재 실행 중인 플러그인
Slot B: 새 버전 준비 영역

전환 흐름:
1. Slot B에 새 버전 다운로드/검증
2. 사전 로딩 (ClassLoader 생성)
3. 적절한 시점에 원자적 전환
4. Slot A는 폴백용으로 유지
```

#### 2.2 핵심 컴포넌트
1. **UpdateManager** - 업데이트 오케스트레이션
2. **DownloadManager** - 증분/재개 다운로드
3. **VersionRegistry** - 버전 메타데이터 관리
4. **SlotManager** - A/B 슬롯 관리
5. **StateSerializer** - 플러그인 상태 저장/복원
6. **HealthChecker** - 업데이트 후 검증
7. **RollbackManager** - 실패 시 자동 롤백

#### 2.3 PluginInterface 확장 필요
```kotlin
interface PluginInterface {
    // 기존 메서드...
    
    // Zero-Downtime 확장
    fun getStateSnapshot(): Bundle  // 상태 내보내기
    fun restoreState(state: Bundle) // 상태 복원
    fun prepareForSwap(): Boolean   // 전환 준비
    fun canSafelySwap(): Boolean    // 안전한 전환 시점 확인
}
```

### 3. 업데이트 흐름

```
[Idle] → [Check] → [Download] → [Verify] → [Stage] → [Wait] → [Swap] → [Validate] → [Commit/Rollback]
                        ↑___________________________|
                        (retry on failure)
```

1. **Check**: 서버에서 새 버전 확인
2. **Download**: 증분 다운로드 (중단 재개 지원)
3. **Verify**: 서명/해시 검증
4. **Stage**: Slot B에 배치, ClassLoader 사전 생성
5. **Wait**: 안전한 전환 시점 대기 (예: 광고 중)
6. **Swap**: 원자적 전환 (상태 마이그레이션 포함)
7. **Validate**: 새 버전 정상 동작 확인
8. **Commit/Rollback**: 성공 시 확정, 실패 시 롤백

## 추론 기록
- STB는 TV 시청 중 업데이트가 보이면 안 됨 → 광고/EPG 화면 등 전환 시점 활용
- 메모리 제약으로 두 버전을 동시에 메모리에 유지하기 어려울 수 있음 → Lazy loading 고려
- 네트워크 끊김 대비 → Delta update + Resume 필수
