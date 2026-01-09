# Reflector Iteration 1: Analysis & Lessons

## Generator 결과 분석

### 핵심 인사이트

1. **Dual-Slot 패턴이 핵심**
   - Zero-downtime의 본질은 "새 버전 준비 완료 후 전환"
   - A/B 슬롯은 이 패턴의 표준 구현

2. **전환 시점 결정이 가장 어려운 문제**
   - 기술적으로 언제든 가능하지만, UX 관점에서 "안전한 시점" 필요
   - STB 특성상 콘텐츠 시청 중단 불가

3. **상태 마이그레이션이 복잡도의 핵심**
   - 단순 코드 교체는 쉬움
   - 실행 중인 상태를 새 버전으로 이관하는 것이 어려움

### 추출된 교훈 (Bullets)

#### Strategies
1. **[STR-001] Dual-Slot A/B Update**
   - 두 슬롯을 번갈아 사용하여 무중단 전환
   - 실패 시 이전 슬롯으로 즉시 롤백 가능

2. **[STR-002] Opportunistic Swap Timing**
   - 안전한 전환 시점을 기다림: 광고, EPG, 메뉴 화면
   - `canSafelySwap()` 인터페이스로 플러그인이 결정

3. **[STR-003] Pre-warming Strategy**
   - 새 버전 ClassLoader를 미리 생성
   - 실제 전환 시 지연 최소화

#### Concepts
1. **[CON-001] Plugin State Portability**
   - 상태는 Bundle/Parcelable로 직렬화
   - 버전 간 스키마 호환성 필요

2. **[CON-002] Graceful Degradation**
   - 새 버전 실패 시 자동 롤백
   - 롤백 카운터로 무한 루프 방지

3. **[CON-003] Delta Updates for STB**
   - 전체 APK 대신 변경분만 전송
   - 네트워크 대역폭 절약

#### Failure Modes
1. **[FAIL-001] Memory Pressure**
   - 두 버전 동시 로딩 시 OOM 가능
   - 해결: Lazy loading, 순차적 해제

2. **[FAIL-002] State Schema Mismatch**
   - 새 버전이 이전 상태를 이해 못 함
   - 해결: 버전별 마이그레이터, 기본값 폴백

3. **[FAIL-003] Incomplete Download**
   - 네트워크 끊김으로 APK 손상
   - 해결: 청크 해시 검증, Resume 지원

## 범용 교훈 (claude-mem 저장 후보)

1. **"Zero-Downtime = 준비 완료 후 원자적 전환"**
   - 이 패턴은 웹서버, 모바일, 임베디드 모두에 적용

2. **"상태 마이그레이션은 스키마 버전 관리 필수"**
   - 데이터베이스 마이그레이션과 동일한 원리

## 다음 Iteration 제안
- 구체적인 클래스 설계 (SlotManager, UpdateManager)
- PluginInterface 확장 명세 상세화
- 에러 복구 시나리오 구체화
