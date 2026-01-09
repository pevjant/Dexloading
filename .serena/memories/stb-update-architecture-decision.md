# STB 무중단 업데이트 아키텍처 결정 (2026-01-09)

## 3-LLM 합의 기반 최종 결정

### 핵심 아키텍처
- **플러그인 프레임워크**: RePlugin 포크 (GLM 권장 채택)
- **ClassLoader 격리**: 새 인스턴스 + BootClassLoader parent
- **IPC 전략**: AIDL(제어) + Shared Memory(데이터) + MediaSession(시스템 통합)
- **Surface 공유**: AIDL로 Surface 객체 직접 전달, SurfaceView 필수

### 플레이어 분리 설계
```
Host Process → AIDL → Player Process (별도)
- 제어 명령: oneway AIDL (0.1ms)
- 대용량 데이터: Shared Memory (5.2ms)
- Crash 복구: IBinder.linkToDeath() + Watchdog
```

### 모듈 전환 패턴
- A/B 슬롯 교대 (dalvik-cache 자동 분리)
- Quiescent State: Reference Counting + Choreographer 프레임 콜백
- Atomic Swap: v2 preload → swap → v1 GC

### 메모리 관리
- GC 모니터링: WeakReference + ReferenceQueue
- 주요 누수 원인: Thread/static/Handler (70%)
- DEX 최적화 파일: 3-5AM 자동 정리

### 구현 우선순위
1. Tier 1 (8-9주): 플레이어 프로세스 분리, AIDL IPC, 기본 핫스왑
2. Tier 2 (6주): RePlugin 포크 통합, A/B 슬롯, 롤백
3. Tier 3 (4주): 모니터링, 메모리 최적화, 장기 테스트

### 검증 기준
- 72시간 무중단 실행 (메모리 증가 < 50MB)
- v1→v2→v1 핫스왑 100회 반복
- 업데이트 중 영상 끊김 0건
- 채널 전환 응답 < 100ms

### 참조 문서
- Claude 분석: /tmp/llm-results/claude-result.md
- Gemini 분석: /tmp/llm-results/gemini-result.md
- GLM 분석: /tmp/llm-results/glm-result.md
