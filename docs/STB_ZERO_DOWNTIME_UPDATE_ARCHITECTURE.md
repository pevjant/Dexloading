# STB 무중단 업데이트 아키텍처 설계

**작성일**: 2026-01-09
**버전**: 1.0
**상태**: 최종 검토 완료

---

## 1. 개요

### 1.1 목적
셋톱박스 런처 앱에서 사용자 시청 경험을 방해하지 않으면서 UI 모듈을 동적으로 업데이트하는 아키텍처 설계

### 1.2 핵심 요구사항

| 요구사항 | 설명 |
|----------|------|
| 무중단 업데이트 | 채널 이동, 컨텐츠 시청 중에도 업데이트 가능 |
| 패치 팝업 회피 | 사용자가 무시하는 패치 팝업 대신 자동 업데이트 |
| 펌웨어 롤백 회피 | 앱 문제 시 앱만 롤백 (펌웨어 전체 롤백 불필요) |
| 메모리 효율 | 장기간 미종료 환경에서 메모리 누수 방지 |

### 1.3 기술 환경

| 항목 | 스펙 |
|------|------|
| 최소 OS | Android 12 (API 31) |
| 앱 타입 | 시스템 앱 (벤더 사이닝) |
| 제조사 협력 | 권한 화이트리스트 요청 가능 |
| 현재 구조 | 단일 Activity + View/Fragment |

---

## 2. 기술 검토 결과

### 2.1 검토 방법
- ACE Framework로 요구사항 고도화 (4회 iteration)
- 3개 LLM (Claude, Gemini, GLM) 병렬 리서치 및 교차 검증
- 사용자 검증 질문을 통한 결과 보정

### 2.2 기존 솔루션 분석

| 솔루션 | 상태 | 즉시 언로딩 | STB 적합성 | 비고 |
|--------|------|------------|-----------|------|
| Tencent Shadow | 활발 | 프로세스 종료 필요 | 높음 | 프로세스 격리 |
| RePlugin | 활발 (v3.1.0, 2024.09) | 프로세스 종료 필요 | 높음 | ClassLoader hook만 사용 |
| Tinker | 활발 | 앱 재시작 필요 | 낮음 | 핫픽스 전용 |
| SplitCompat | Google 유지 | N/A | 낮음 | GMS/Play Store 필수 |
| Qigsaw | 중단/삭제 | - | - | 레포지토리 404 |
| VirtualAPK | 노후화 | - | 낮음 | Android 9까지 |

### 2.3 핵심 기술적 발견

#### ClassLoader 언로딩 불가
```
Android에서 ClassLoader/클래스의 즉시 언로딩은 원천적으로 불가능
→ 해결책: 프로세스 종료/재시작
```

#### 모든 프레임워크의 공통 한계
```
Shadow, RePlugin 모두 "핫 언로딩" 미지원
→ 플러그인 프로세스 종료 후 재시작으로 메모리 정리
```

### 2.4 권장 접근법: 직접 구현

외부 프레임워크 대신 **Sandbox Activity 패턴 직접 구현** 권장

**이유**:
- 외부 의존성 제거
- 직접 제어 가능
- 코드베이스 단순화
- Shadow/RePlugin과 동일한 원리 (프로세스 격리)

---

## 3. 최종 아키텍처

### 3.1 전체 구조도

```
┌─────────────────────────────────────────────────────────────────┐
│                        Host Process                              │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    MainActivity                            │  │
│  │  ┌─────────────────┐  ┌─────────────────────────────────┐ │  │
│  │  │ PlayerView      │  │ Quick UI ViewGroup              │ │  │
│  │  │ (SurfaceView)   │  │ - 채널 번호                      │ │  │
│  │  │                 │  │ - 멀티뷰                         │ │  │
│  │  └─────────────────┘  └─────────────────────────────────┘ │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ Popup ViewGroup                                      │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ ModuleManager                                        │  │  │
│  │  │ - 버전 체크                                          │  │  │
│  │  │ - 다운로드 관리                                       │  │  │
│  │  │ - Sandbox 실행/종료                                   │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ startActivity (IPC)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Sandbox Process (:sandbox)                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   SandboxActivity                          │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ DexClassLoader                                       │  │  │
│  │  │ - 모듈 APK/DEX 로드                                  │  │  │
│  │  │ - 버전별 고유 경로                                    │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ PluginFragment (동적 로드)                           │  │  │
│  │  │ - VOD 목록                                          │  │  │
│  │  │ - VOD 상세                                          │  │  │
│  │  │ - 설정                                              │  │  │
│  │  │ - 기타 UI                                           │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  업데이트 시: finish() + Process.killProcess()                   │
│  → Host가 새 모듈로 Sandbox 재시작                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ MediaSession (IPC)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Player Process (:player)                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   PlayerService                            │  │
│  │  ┌─────────────────┐  ┌─────────────────────────────────┐ │  │
│  │  │ ExoPlayer       │  │ MediaSession                    │ │  │
│  │  │ (Media3)        │  │ - 상태 브로드캐스트              │ │  │
│  │  │                 │  │ - 제어 명령 수신                 │ │  │
│  │  └─────────────────┘  └─────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  완전 독립: UI 업데이트와 무관하게 재생 유지                       │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 프로세스 분리 전략

| 프로세스 | 역할 | 업데이트 영향 |
|----------|------|--------------|
| Host (메인) | ModuleManager, Quick UI, Popup | 업데이트 안함 |
| :sandbox | 동적 UI 모듈 | 모듈 업데이트 시 재시작 |
| :player | 미디어 재생 | 업데이트 무관 |

### 3.3 업데이트 플로우

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 업데이트 감지                                             │
│    ModuleManager가 서버에서 새 버전 확인                      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 백그라운드 다운로드                                        │
│    새 모듈 APK/DEX 다운로드 (시청 중에도 진행)                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 적용 시점 대기                                            │
│    - Quiescent State 감지 (사용자 조작 없는 대기 상태)         │
│    - 또는 다음 Sandbox 진입 시점                              │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Sandbox 종료                                              │
│    - SandboxActivity.finish()                               │
│    - Process.killProcess() (프로세스 완전 종료)               │
│    - 이전 ClassLoader 메모리 완전 해제                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 새 Sandbox 시작                                           │
│    - 새 모듈 경로로 SandboxActivity 시작                      │
│    - 새 DexClassLoader 생성                                  │
│    - 새 PluginFragment 로드                                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. 검증                                                      │
│    - HealthCheck (10초 이내 정상 동작 확인)                   │
│    - 실패 시 이전 버전으로 롤백                               │
└─────────────────────────────────────────────────────────────┘
```

### 3.4 A/B 슬롯 버전 관리

```
/data/app/com.example.stb/modules/
├── slot-a/
│   ├── ui-module.apk          (v2.0.0, 현재 활성)
│   ├── metadata.json
│   └── dex-cache/
└── slot-b/
    ├── ui-module.apk          (v1.9.0, 롤백용)
    ├── metadata.json
    └── dex-cache/
```

**metadata.json**:
```json
{
  "version": "2.0.0",
  "installedAt": "2026-01-09T10:30:00Z",
  "sha256": "abc123...",
  "status": "active",
  "healthCheckPassed": true
}
```

---

## 4. 핵심 컴포넌트 설계

### 4.1 ModuleManager

```kotlin
class ModuleManager(
    private val context: Context,
    private val moduleRepository: ModuleRepository
) {
    private var currentSlot: Slot = Slot.A
    private var sandboxConnection: SandboxConnection? = null

    // 버전 체크 및 다운로드
    suspend fun checkForUpdates(): UpdateResult {
        val serverVersion = moduleRepository.getLatestVersion()
        val localVersion = getActiveModuleVersion()

        if (serverVersion > localVersion) {
            return downloadToInactiveSlot(serverVersion)
        }
        return UpdateResult.NoUpdate
    }

    // Sandbox 시작
    fun launchSandbox() {
        val modulePath = getActiveModulePath()
        val intent = Intent(context, SandboxActivity::class.java).apply {
            putExtra(EXTRA_MODULE_PATH, modulePath)
            putExtra(EXTRA_MODULE_VERSION, getActiveModuleVersion())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // 모듈 업데이트 적용
    fun applyUpdate() {
        // 1. Sandbox 종료 요청
        sandboxConnection?.requestShutdown()

        // 2. 슬롯 전환
        currentSlot = currentSlot.other()

        // 3. 새 Sandbox 시작
        launchSandbox()

        // 4. HealthCheck
        scheduleHealthCheck()
    }

    // 롤백
    fun rollback() {
        currentSlot = currentSlot.other()
        launchSandbox()
    }
}
```

### 4.2 SandboxActivity

```kotlin
class SandboxActivity : AppCompatActivity() {
    private var moduleClassLoader: DexClassLoader? = null
    private var pluginFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox)

        val modulePath = intent.getStringExtra(EXTRA_MODULE_PATH)!!
        val moduleVersion = intent.getStringExtra(EXTRA_MODULE_VERSION)!!

        loadModule(modulePath, moduleVersion)
        registerShutdownReceiver()
    }

    private fun loadModule(modulePath: String, version: String) {
        // 버전별 고유 캐시 디렉토리
        val optimizedDir = File(codeCacheDir, "dex_$version")
        optimizedDir.mkdirs()

        moduleClassLoader = DexClassLoader(
            modulePath,
            optimizedDir.absolutePath,
            null,
            classLoader
        )

        // Fragment 로드
        val fragmentClass = moduleClassLoader!!
            .loadClass("com.plugin.ui.MainFragment")
        pluginFragment = (fragmentClass.newInstance() as Fragment).apply {
            arguments = intent.extras
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.plugin_container, pluginFragment!!)
            .commit()

        // HealthCheck 성공 알림
        notifyHealthCheckSuccess()
    }

    private fun registerShutdownReceiver() {
        // Host로부터 종료 요청 수신
        val filter = IntentFilter(ACTION_SHUTDOWN_SANDBOX)
        registerReceiver(shutdownReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            shutdown()
        }
    }

    private fun shutdown() {
        finish()
        // 프로세스 완전 종료 → 메모리 완전 해제
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(shutdownReceiver)
        pluginFragment = null
        moduleClassLoader = null
    }
}
```

### 4.3 PlayerService

```kotlin
class PlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
```

### 4.4 Host-Sandbox IPC

```kotlin
// AIDL 인터페이스
interface ISandboxCallback {
    void onHealthCheckSuccess();
    void onHealthCheckFailed(String reason);
    void onUserAction(String action, in Bundle data);
}

interface IHostService {
    void requestModuleUpdate();
    void notifyPlaybackState(int state, long position);
    void requestPlayerControl(String command, in Bundle params);
}
```

---

## 5. 플러그인 인터페이스 설계

### 5.1 공유 인터페이스 모듈

Host와 Plugin이 공유하는 인터페이스 정의 (별도 AAR로 배포)

```kotlin
// shared-interface/src/main/java/com/stb/plugin/api/

/**
 * 플러그인 진입점
 * @since 1.0.0
 */
interface PluginEntry {
    fun createMainFragment(): Fragment
    fun getVersion(): String

    /** @since 1.1.0 */
    fun getSupportedFeatures(): List<String> = emptyList()
}

/**
 * Host 기능 접근용
 * @since 1.0.0
 */
interface HostBridge {
    fun getPlayerController(): PlayerController
    fun showPopup(type: PopupType, data: Bundle)
    fun dismissPopup()
    fun navigateTo(destination: String, params: Bundle)

    /** @since 1.2.0 */
    fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN
}

/**
 * 플레이어 제어
 * @since 1.0.0
 */
interface PlayerController {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setMedia(uri: String, metadata: Bundle)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun getPlaybackState(): PlaybackState
}
```

### 5.2 버전 호환성 규칙

| 변경 유형 | 허용 여부 | 처리 방법 |
|-----------|----------|----------|
| 메서드 추가 | ✅ | default 구현 제공 |
| 메서드 제거 | ❌ | @Deprecated 후 N+2 버전에서 제거 |
| 파라미터 추가 | ❌ | 새 메서드로 오버로드 |
| 반환 타입 변경 | ❌ | 새 메서드 정의 |

---

## 6. 빌드 및 배포

### 6.1 모듈 구조

```
stb-app/
├── host-app/                    # 메인 APK (시스템 앱)
│   ├── src/main/
│   └── build.gradle.kts
├── player-service/              # 플레이어 (별도 프로세스)
│   ├── src/main/
│   └── build.gradle.kts
├── sandbox-container/           # Sandbox Activity
│   ├── src/main/
│   └── build.gradle.kts
├── shared-interface/            # 공유 인터페이스 (AAR)
│   ├── src/main/
│   └── build.gradle.kts
└── ui-plugin/                   # 동적 UI 모듈 (별도 배포)
    ├── src/main/
    └── build.gradle.kts
```

### 6.2 플러그인 APK 빌드

```kotlin
// ui-plugin/build.gradle.kts
plugins {
    id("com.android.application")  // APK로 빌드
    kotlin("android")
}

android {
    namespace = "com.stb.plugin.ui"

    defaultConfig {
        applicationId = "com.stb.plugin.ui"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    compileOnly(project(":shared-interface"))  // 런타임에는 Host가 제공
}
```

### 6.3 배포 파이프라인

```
1. 플러그인 개발/테스트
       ↓
2. 버전 태깅 (v1.0.0)
       ↓
3. CI/CD 빌드
   - APK 생성
   - SHA256 해시 계산
   - 서명 검증
       ↓
4. CDN 업로드
   - ui-plugin-v1.0.0.apk
   - metadata.json
       ↓
5. 버전 서버 업데이트
   - 최신 버전 정보
   - 다운로드 URL
   - 최소 호스트 버전
       ↓
6. 단계적 롤아웃
   - 1% → 5% → 25% → 100%
```

---

## 7. 안정성 전략

### 7.1 Multi-layer Recovery

```
Layer 1: HealthCheck (업데이트 직후)
    ↓ 실패
Layer 2: Auto Rollback (이전 버전으로)
    ↓ 실패
Layer 3: Startup Watchdog (크래시 감지)
    ↓ 실패
Layer 4: Factory Reset (최후 수단)
```

### 7.2 HealthCheck 구현

```kotlin
class HealthChecker(
    private val timeout: Long = 10_000L
) {
    suspend fun check(sandboxConnection: SandboxConnection): HealthResult {
        return withTimeoutOrNull(timeout) {
            // 1. Sandbox 프로세스 생존 확인
            if (!sandboxConnection.isAlive()) {
                return@withTimeoutOrNull HealthResult.ProcessDead
            }

            // 2. UI 렌더링 확인
            val uiReady = sandboxConnection.isUiReady()
            if (!uiReady) {
                return@withTimeoutOrNull HealthResult.UiNotReady
            }

            // 3. 기본 기능 동작 확인
            val functionalTest = sandboxConnection.runFunctionalTest()
            if (!functionalTest.success) {
                return@withTimeoutOrNull HealthResult.FunctionalTestFailed(functionalTest.error)
            }

            HealthResult.Success
        } ?: HealthResult.Timeout
    }
}
```

### 7.3 Startup Watchdog

```kotlin
class StartupWatchdog(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val CRASH_THRESHOLD_MS = 10_000L
        private const val MAX_CRASH_COUNT = 3
    }

    fun onAppStart() {
        val lastStartTime = prefs.getLong("last_start_time", 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastStartTime < CRASH_THRESHOLD_MS) {
            // 빠른 재시작 = 크래시로 간주
            val crashCount = prefs.getInt("crash_count", 0) + 1
            prefs.edit().putInt("crash_count", crashCount).apply()

            if (crashCount >= MAX_CRASH_COUNT) {
                triggerRollback()
            }
        } else {
            // 정상 시작
            prefs.edit().putInt("crash_count", 0).apply()
        }

        prefs.edit().putLong("last_start_time", currentTime).apply()
    }

    fun onAppStable() {
        // 30초 이상 정상 동작 후 호출
        prefs.edit()
            .putInt("crash_count", 0)
            .putLong("stable_time", System.currentTimeMillis())
            .apply()
    }
}
```

### 7.4 Rollback Budget

```kotlin
data class RollbackBudget(
    val maxCount: Int = 3,
    val cooldownMs: Long = 60_000L
) {
    private var count = 0
    private var lastRollbackTime = 0L

    fun canRollback(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRollbackTime > cooldownMs) {
            count = 0  // 쿨다운 지나면 리셋
        }
        return count < maxCount
    }

    fun consumeRollback() {
        count++
        lastRollbackTime = System.currentTimeMillis()
    }

    fun onSuccessfulUpdate() {
        count = 0
    }
}
```

---

## 8. 리스크 및 완화 전략

| 리스크 | 영향 | 확률 | 완화 전략 |
|--------|------|------|----------|
| Sandbox 프로세스 크래시 | 중 | 중 | HealthCheck + Auto Rollback |
| 메모리 누수 누적 | 고 | 저 | 프로세스 격리로 원천 차단 |
| 네트워크 다운로드 실패 | 저 | 중 | Resume 지원 + 재시도 |
| 버전 호환성 문제 | 고 | 중 | 엄격한 인터페이스 버전 관리 |
| 롤백 무한 루프 | 고 | 저 | Rollback Budget 제한 |
| 업데이트 중 전원 차단 | 고 | 저 | A/B 슬롯으로 이전 버전 보존 |
| 시각적 끊김 | 저 | 고 | 투명 테마 + 빠른 전환 (1초 미만) |

---

## 9. 구현 로드맵

### Phase 1: 기반 구축 (2-3주)

- [ ] 공유 인터페이스 모듈 정의
- [ ] ModuleManager 스켈레톤
- [ ] A/B 슬롯 구조 구현
- [ ] 버전 관리 로직

### Phase 2: Sandbox 구현 (2주)

- [ ] SandboxActivity 구현
- [ ] DexClassLoader 통합
- [ ] Host-Sandbox IPC (AIDL)
- [ ] 종료/재시작 메커니즘

### Phase 3: 플레이어 분리 (2주)

- [ ] PlayerService 구현 (Media3)
- [ ] MediaSession 통합
- [ ] Host-Player IPC
- [ ] 생명주기 관리

### Phase 4: 안정성 (2주)

- [ ] HealthCheck 구현
- [ ] Startup Watchdog
- [ ] Auto Rollback
- [ ] Rollback Budget

### Phase 5: 배포 파이프라인 (1주)

- [ ] 플러그인 빌드 자동화
- [ ] CDN 연동
- [ ] 버전 서버 구현
- [ ] 단계적 롤아웃 로직

### Phase 6: 테스트 및 안정화 (2주)

- [ ] 72시간 안정성 테스트
- [ ] 메모리 프로파일링
- [ ] 다양한 시나리오 테스트
- [ ] 카나리 릴리스

---

## 10. 참고 자료

### 공식 문서
- [AOSP - Configure ART](https://source.android.com/docs/core/runtime/configure)
- [Android Developers - MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [AOSP - Privileged permission allowlist](https://source.android.com/docs/core/permissions/perms-allowlist)

### 오픈소스 프로젝트 (참고용)
- [Tencent Shadow](https://github.com/Tencent/Shadow) - 프로세스 격리 패턴 참고
- [RePlugin](https://github.com/Qihoo360/RePlugin) - ClassLoader 관리 참고

### 관련 문서
- ACE Playbook: `.serena/memories/ace_stb_zero_downtime_update_playbook.md`
- 3-LLM 리서치 통합: `/tmp/claude-prompts/research-consolidated.md`

---

## 부록 A: AndroidManifest.xml 예시

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.stb">

    <!-- 시스템 권한 (제조사 화이트리스트 필요) -->
    <uses-permission android:name="android.permission.INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.DELETE_PACKAGES" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:name=".STBApplication"
        android:persistent="true">

        <!-- Host Activity (메인 프로세스) -->
        <activity
            android:name=".MainActivity"
            android:launchMode="singleTask"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Sandbox Activity (별도 프로세스) -->
        <activity
            android:name=".sandbox.SandboxActivity"
            android:process=":sandbox"
            android:theme="@style/Theme.Transparent"
            android:exported="false" />

        <!-- Player Service (별도 프로세스) -->
        <service
            android:name=".player.PlayerService"
            android:process=":player"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

---

## 부록 B: 결정 근거 요약

| 결정 사항 | 선택 | 근거 |
|-----------|------|------|
| 프레임워크 | 직접 구현 | 외부 의존성 제거, 동일 원리 |
| 프로세스 격리 | ✅ 사용 | 메모리 완전 해제 보장 |
| UI 모듈화 | Sandbox Activity | Fragment 언로딩 불가 |
| 플레이어 분리 | 별도 프로세스 | 업데이트 무관 재생 유지 |
| 버전 관리 | A/B 슬롯 | 즉시 롤백 가능 |
| IPC | AIDL + MediaSession | 구조화된 통신 + 표준 미디어 제어 |
