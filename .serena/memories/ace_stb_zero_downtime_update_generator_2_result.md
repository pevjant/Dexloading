# Generator Result: Iteration 2 - Interface & Implementation Design

## 설계 범위
Playbook 전략을 적용한 구체적 인터페이스 및 구현 상세 설계

## 1. 확장된 PluginInterface

```kotlin
interface PluginInterface {
    // 기존 메서드
    fun initialize(hostContext: Context, pluginResources: Resources, pluginAssets: AssetManager)
    fun getName(): String
    fun getVersion(): String
    fun createView(hostContext: Context): View
    fun onDestroy()
    
    // 신규: 버전 호환성
    fun getMinHostVersion(): Int
    fun getMaxHostVersion(): Int
    
    // 신규: 상태 관리 (S-002)
    fun saveState(): Bundle?
    fun restoreState(state: Bundle?)
    fun getStateSchemaVersion(): Int
    
    // 신규: 헬스 체크
    fun performHealthCheck(): HealthCheckResult
}

data class HealthCheckResult(
    val isHealthy: Boolean,
    val failureReason: String? = null,
    val metrics: Map<String, Any> = emptyMap()
)
```

## 2. PluginWrapper (S-001: Quiescent State Detection)

```kotlin
class PluginWrapper(val plugin: PluginInterface) {
    private val refCount = AtomicInteger(0)
    
    fun acquire(): PluginInterface {
        refCount.incrementAndGet()
        return plugin
    }
    
    fun release() {
        refCount.decrementAndGet()
    }
    
    fun isQuiescent(): Boolean = refCount.get() == 0
    fun getActiveCount(): Int = refCount.get()
}
```

## 3. PluginSwapper (핵심 Hot Swap 로직)

```kotlin
class PluginSwapper(
    private val pluginLoader: PluginLoader,
    private val swapTimeout: Long = 30_000L
) {
    private val currentWrapper = AtomicReference<PluginWrapper?>(null)
    
    suspend fun swapPlugin(apkFile: File): Result<PluginInterface>
    fun acquirePlugin(): PluginInterface?
    fun releasePlugin()
}
```

핵심 로직:
1. 새 플러그인 선로드 (서비스 중단 없음)
2. Quiescent 상태 대기 (타임아웃 30초)
3. AtomicReference로 원자적 스왑
4. 이전 버전 정리 (onDestroy, unload)

## 4. StateRepository (S-002: Version-Agnostic State)

```kotlin
interface StateRepository {
    suspend fun saveCurrentState(): StateSnapshot
    suspend fun restoreState(snapshot: StateSnapshot): Result<Unit>
    suspend fun migrateState(from: Int, to: Int, state: Bundle): Bundle
}

data class StateSnapshot(
    val schemaVersion: Int,
    val pluginVersion: String,
    val data: Bundle,
    val timestamp: Long
)
```

마이그레이션 전략:
- 순차적 마이그레이션 (1→2→3 또는 3→2→1)
- 각 버전 쌍에 대한 StateMigrator 등록
- 마이그레이터 없으면 예외 발생

## 5. VersionManager (S-003: Lean A/B)

```kotlin
interface VersionManager {
    fun getCurrentVersion(): VersionInfo
    fun getPreviousVersions(): List<VersionInfo>
    fun getMinimalFallbackVersion(): VersionInfo?
    
    suspend fun markUpdateComplete(newVersion: String)
    suspend fun prepareRollback(): RollbackPlan
}

data class VersionInfo(
    val version: String,
    val localPath: String?,        // null이면 원격만 보관
    val remoteUrl: String,
    val sha256: String,
    val isMinimal: Boolean = false
)
```

## 6. UpdateManager (오케스트레이터)

```kotlin
class UpdateManager(...) {
    sealed class UpdateState {
        object Idle : UpdateState()
        data class Downloading(val progress: Int) : UpdateState()
        object Downloaded : UpdateState()
        object Installing : UpdateState()
        object HealthChecking : UpdateState()
        data class Completed(val version: String) : UpdateState()
        data class Failed(val error: Throwable) : UpdateState()
        object RollingBack : UpdateState()
    }
    
    suspend fun performUpdate(): Result<Unit>
}
```

업데이트 플로우:
1. 상태 저장 → 2. 다운로드 → 3. 핫 스왑 → 4. 상태 복원 → 5. 헬스 체크 → 6. 완료

## 7. HealthChecker (Composite Pattern)

```kotlin
class CompositeHealthChecker(
    private val checks: List<HealthCheck>,
    private val maxRetries: Int = 3
) : HealthChecker

interface HealthCheck {
    suspend fun execute(): HealthCheckResult
}

// 구현체: PluginLoadCheck, UIRenderCheck, NetworkConnectivityCheck
```

## 구현 우선순위

| Phase | 컴포넌트 | 의존성 |
|-------|---------|--------|
| 1 (MVP) | PluginInterface 확장 | - |
| 1 | PluginWrapper | PluginInterface |
| 1 | PluginSwapper | PluginWrapper, PluginLoader |
| 2 | StateRepository | - |
| 2 | VersionManager | - |
| 3 | DownloadManager | - |
| 3 | UpdateManager | 모든 컴포넌트 |
