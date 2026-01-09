# 셋톱박스 무중단 업데이트 아키텍처 - 최종 보고서

**작성일**: 2026-01-09
**조사 방법**: 3-LLM 병렬 조사 (Claude, Gemini, GLM)
**질문 수**: 16개 (6개 카테고리)

---

## 1. 3-LLM 합의점 (High Confidence)

| 항목 | 합의 내용 | 신뢰도 |
|------|----------|--------|
| ClassLoader 격리 | 새 인스턴스 + BootClassLoader parent | ⭐⭐⭐ |
| 프록시 패턴 | 인터페이스 기반 (~8ns), Reflection 회피 (~1000ns) | ⭐⭐⭐ |
| IPC 기본 | AIDL + MediaSession 하이브리드 | ⭐⭐⭐ |
| Surface 전달 | AIDL로 Surface 객체 직접 전달 | ⭐⭐⭐ |
| SurfaceView | TextureView 대비 메모리/레이턴시 우위, 24/7 필수 | ⭐⭐⭐ |
| Crash 복구 | IBinder.linkToDeath() + Watchdog | ⭐⭐⭐ |
| 메모리 누수 | Thread/static/Handler가 70% 원인 | ⭐⭐⭐ |
| GC 모니터링 | WeakReference + ReferenceQueue 패턴 | ⭐⭐⭐ |

---

## 2. 이견점 분석 및 결정

### 2-1. 플러그인 프레임워크 선택

| LLM | 1순위 | 근거 |
|-----|-------|------|
| **GLM** | RePlugin | Android 12+ 호환성, 프로덕션 사례 다수, STB 최적화 |
| Claude | RePlugin 변형 | Shadow 대비 복잡도 낮음, 커스터마이징 용이 |
| Gemini | Shadow → RePlugin | Shadow 선 검토 후 RePlugin 권장 |

**결정: RePlugin 포크** (GLM 권장 채택)

**근거**:
- Android 12+ SELinux 제약 대응 완료
- 메모리 256MB 환경 검증 사례 존재
- 장기 실행 안정성 입증

### 2-2. IPC 최적화 전략

| LLM | 접근법 | 레이턴시 |
|-----|--------|----------|
| GLM | Shared Memory + AIDL | 5.2ms (데이터), 0.5ms (제어) |
| Claude | oneway AIDL + 예측 버퍼링 | 0.1ms (제어) |
| Gemini | MediaSession 중심 | 14ms (표준) |

**결정: 3계층 하이브리드**

```
제어 명령 (채널 전환) → oneway AIDL (0.1ms)
대용량 데이터 (EPG) → Shared Memory (5.2ms)
시스템 통합 (HDMI CEC) → MediaSession
```

### 2-3. 리모컨 입력 지연 방지

| LLM | 전략 |
|-----|------|
| Claude | Optimistic UI + 비동기 처리 |
| GLM | Input 전용 스레드 + 우선순위 큐 |
| Gemini | MediaSession 우선순위 |

**결정: Optimistic UI 패턴 채택**

```kotlin
// UI 즉시 반영 → 백그라운드 처리 → 실패 시 롤백
fun onChannelChange(ch: Int) {
    showChannelOverlay(ch)  // 0ms - 즉시 표시
    playerService.changeChannel(ch)  // async
        .onFailure { revertOverlay() }
}
```

---

## 3. 각 LLM 고유 인사이트

### Claude
- **Choreographer 활용**: 프레임 콜백으로 Quiescent State 감지
- **ModuleSwapManager**: Atomic swap + rollback 완전 구현 예시
- **성능 비교표**: 프록시 vs Reflection 정량 데이터

### Gemini
- **SurfaceControlViewHost**: Android 10+ Surface 공유 대안
- **WebView + Native 하이브리드**: UI는 웹, 플레이어는 네이티브
- **A/B 파티션 스타일**: 앱 레벨 슬롯 교체 접근법

### GLM
- **벤치마크 데이터**: IPC 레이턴시 실측치 (Shared Memory 5.2ms)
- **구현 타임라인**: 3-Tier 단계별 공수 산정
- **중국 프로덕션 사례**: 샤오미/화웨이 STB 적용 경험

---

## 4. 최종 아키텍처 권고

```
┌─────────────────────────────────────────────────────────┐
│                    Host App (Main Process)              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │PluginLoader │  │ModuleSwapMgr│  │  UI Shell   │     │
│  │(RePlugin변형)│  │(Atomic Swap)│  │(Optimistic) │     │
│  └──────┬──────┘  └──────┬──────┘  └─────────────┘     │
│         │                │                              │
│    ┌────┴────┐     ┌─────┴─────┐                       │
│    │Slot A/B │     │ Reference │                       │
│    │Alternation│   │ Counting  │                       │
│    └─────────┘     └───────────┘                       │
└──────────────────────┬──────────────────────────────────┘
                       │ AIDL + Shared Memory
┌──────────────────────┴──────────────────────────────────┐
│                  Player Process (Isolated)              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   Player    │  │   Surface   │  │   State     │     │
│  │   Core      │  │   Provider  │  │  Watchdog   │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────┘
```

### 핵심 컴포넌트 설명

| 컴포넌트 | 역할 | 위치 |
|----------|------|------|
| PluginLoader | DEX 로딩, ClassLoader 관리 | Host |
| ModuleSwapMgr | A/B 슬롯 교대, Atomic Swap | Host |
| UI Shell | Optimistic UI, Fragment 관리 | Host |
| Player Core | 영상 재생, 코덱 처리 | Player Process |
| Surface Provider | SurfaceView 관리, AIDL 전달 | Player Process |
| State Watchdog | Health Check, Crash 복구 | Player Process |

---

## 5. 구현 로드맵

| Phase | 기간 | 내용 | 우선순위 |
|-------|------|------|----------|
| **Tier 1** | 8-9주 | 플레이어 프로세스 분리, AIDL IPC, 기본 핫스왑 | Must |
| **Tier 2** | 6주 | RePlugin 포크 통합, A/B 슬롯, 롤백 | Should |
| **Tier 3** | 4주 | 모니터링, 메모리 최적화, 장기 테스트 | Nice-to-have |

### Tier 1 세부 (8-9주)

| 주차 | 작업 |
|------|------|
| 1-2 | 플레이어 프로세스 분리, Service 구조 설계 |
| 3-4 | AIDL 인터페이스 정의, Surface 전달 구현 |
| 5-6 | IPC 통신 구현, Shared Memory 설정 |
| 7-8 | 기본 핫스왑 로직, ClassLoader 격리 |
| 9 | 통합 테스트, 버그 수정 |

### Tier 2 세부 (6주)

| 주차 | 작업 |
|------|------|
| 1-2 | RePlugin 분석, 포크 준비 |
| 3-4 | A/B 슬롯 구현, Atomic Swap |
| 5-6 | 롤백 메커니즘, 버전 관리 |

### Tier 3 세부 (4주)

| 주차 | 작업 |
|------|------|
| 1-2 | 메모리 모니터링, WeakRef 패턴 적용 |
| 3-4 | 72시간 스트레스 테스트, 최적화 |

---

## 6. 리스크 매트릭스

| 리스크 | 심각도 | 발생 확률 | 완화 전략 |
|--------|--------|----------|----------|
| Android 14 DEX 정책 변경 | High | Medium | AGP 8.x 호환 검증, 대안 준비 |
| 벤더 ROM 호환성 | Medium | High | 삼성/LG 타겟 테스트 우선 |
| 장기 메모리 누수 | Critical | Medium | WeakRef 모니터 + 72시간 스트레스 테스트 |
| IPC 레이턴시 스파이크 | Medium | Low | Watchdog + Circuit Breaker |
| 롤백 실패 | High | Low | 최소 2버전 유지, Factory Reset Fallback |

### 리스크 대응 상세

#### Android 14 DEX 정책 변경
- **모니터링**: Android 14 QPR 릴리스 노트 추적
- **대안**: Play Feature Delivery (GMS 의존), 앱 전체 교체

#### 벤더 ROM 호환성
- **우선 타겟**: 삼성 Tizen-Android, LG webOS-Android
- **테스트 매트릭스**: 최소 5개 벤더 × 3개 버전

#### 장기 메모리 누수
- **측정 기준**: 72시간 후 메모리 증가 < 50MB
- **도구**: Android Profiler, LeakCanary, Native 메모리 추적

---

## 7. 검증 체크리스트

### 기능 검증

- [ ] 플레이어 프로세스 분리 동작
- [ ] AIDL Surface 전달 성공
- [ ] 채널 전환 100ms 이내 응답
- [ ] 핫스왑 중 영상 연속 재생
- [ ] Crash 복구 3초 이내

### 안정성 검증

- [ ] 72시간 무중단 실행 (메모리 증가 < 50MB)
- [ ] v1→v2→v1 핫스왑 100회 반복
- [ ] 업데이트 중 영상 끊김 0건
- [ ] 롤백 성공률 100%

### 성능 검증

- [ ] IPC 레이턴시: 제어 < 1ms, 데이터 < 10ms
- [ ] 메모리 사용: Host < 100MB, Player < 150MB
- [ ] CPU 사용: Idle 시 < 5%

---

## 8. 참조 자료

### 분석 결과 파일

| 파일 | 설명 |
|------|------|
| `/tmp/llm-results/claude-result.md` | Claude 상세 분석 (3,591 라인) |
| `/tmp/llm-results/gemini-result.md` | Gemini 구조화 분석 (493 라인) |
| `/tmp/llm-results/glm-result.md` | GLM 벤치마크 분석 (3,679 라인) |

### 관련 프레임워크

| 프레임워크 | GitHub | 용도 |
|------------|--------|------|
| RePlugin | Qihoo360/RePlugin | 플러그인 로딩 |
| Shadow | Tencent/Shadow | 대안 검토 |
| Tinker | Tencent/tinker | 핫픽스 참조 |

### 공식 문서

- [Android ClassLoader](https://developer.android.com/reference/java/lang/ClassLoader)
- [AIDL](https://developer.android.com/guide/components/aidl)
- [SurfaceView](https://developer.android.com/reference/android/view/SurfaceView)

---

## 9. 결론

3개 LLM의 독립적 분석 결과, **RePlugin 포크 + AIDL/Shared Memory 하이브리드 IPC + A/B 슬롯 교대** 아키텍처가 셋톱박스 무중단 업데이트에 최적임을 확인했습니다.

핵심 성공 요소:
1. **플레이어 프로세스 분리**: 업데이트 중 재생 연속성 보장
2. **ClassLoader 격리**: 캐싱 문제 회피
3. **Optimistic UI**: 리모컨 반응성 유지
4. **메모리 모니터링**: 장기 실행 안정성 확보

예상 총 구현 기간: **18-19주** (Tier 1-3 합산)
