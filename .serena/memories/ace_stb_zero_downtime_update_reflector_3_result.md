# Reflector Iteration 3: Error Recovery Analysis

## Generator 결과 분석

### 핵심 인사이트

1. **다층 방어(Defense in Depth)**
   - HealthChecker → RollbackManager → StartupWatchdog → FactoryReset
   - 각 층이 실패해도 다음 층에서 복구

2. **복구 비용 최소화**
   - 롤백은 이전 슬롯 활성화만 (재다운로드 불필요)
   - Factory reset은 최후의 수단 (UX 비용 높음)

3. **테스트 피라미드 적용**
   - Unit > Integration > E2E
   - 상태 머신 로직은 Unit으로 철저히 검증

### 추출된 교훈 (Bullets)

#### Strategies (추가)
1. **[STR-006] Multi-layer Recovery**
   - 여러 복구 메커니즘을 계층화
   - 각 층은 독립적으로 동작
   - 최악의 경우에도 동작하는 Factory Reset 보장

2. **[STR-007] Startup Watchdog Pattern**
   - 앱 시작 시간 추적으로 크래시 루프 감지
   - N회 연속 빠른 재시작 = 크래시로 판단
   - 자동 롤백으로 사용자 개입 없이 복구

#### Concepts (추가)
1. **[CON-006] Rollback Budget**
   - 롤백 횟수 제한 (기본 3회)
   - 쿨다운 시간으로 너무 빠른 롤백 방지
   - 예산 소진 시 수동 개입 요청

2. **[CON-007] Recovery Cost Hierarchy**
   ```
   Low Cost:  Slot Swap (< 1초)
   Med Cost:  Re-download (분~시간)
   High Cost: Factory Reset (설정 초기화)
   ```

#### Failure Modes (추가)
1. **[FAIL-006] Rollback Loop**
   - 증상: 새 버전과 이전 버전 모두 실패
   - 원인: 공통 의존성 문제, 손상된 저장소
   - 해결: MAX_ROLLBACK_COUNT 후 Factory Reset

2. **[FAIL-007] Watchdog False Positive**
   - 증상: 정상 재시작을 크래시로 오인
   - 원인: 사용자 빠른 앱 전환, Force Stop
   - 해결: CRASH_THRESHOLD 조정, 명시적 종료 플래그

## 범용 교훈

1. **"복구 메커니즘은 계층화하되, 마지막은 항상 동작해야 함"**
   - Factory Reset은 실패할 수 없어야 함

2. **"상태 머신 로직은 Unit Test로 100% 커버"**
   - 허용되지 않는 전이는 컴파일/런타임에 차단

## 다음 Iteration 제안
- 최종 아키텍처 다이어그램
- 구현 로드맵 및 우선순위
- STB 특화 최적화
