package com.example.dexloadingtest.examples

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * PackageInstaller를 사용한 앱 재시작 없는 Split APK 설치
 *
 * 핵심:
 * - MODE_INHERIT_EXISTING: base APK 재설치 불필요
 * - setDontKillApp(true): 앱 재시작 방지 (Android 8.0+)
 */
class SplitApkInstaller(private val context: Context) {

    companion object {
        private const val TAG = "SplitApkInstaller"
        private const val INSTALL_ACTION = "com.example.SPLIT_INSTALL_RESULT"
    }

    private var installResultReceiver: InstallResultReceiver? = null

    /**
     * Split APK 설치 (재시작 없음)
     *
     * @param splitApkPath Split APK 파일 경로
     * @param splitName Split 이름 (예: "feature_camera")
     * @param dontKillApp 앱 재시작 방지 여부 (Android 8.0+만 지원)
     */
    fun installSplitApk(
        splitApkPath: String,
        splitName: String = "feature",
        dontKillApp: Boolean = true,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        try {
            val apkFile = File(splitApkPath)
            if (!apkFile.exists()) {
                onResult(false, "APK file not found: $splitApkPath")
                return
            }

            // 1. InstallResultReceiver 등록
            registerInstallReceiver(onResult)

            // 2. PackageInstaller 가져오기
            val packageInstaller = context.packageManager.packageInstaller

            // 3. SessionParams 생성
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_INHERIT_EXISTING  // ← base APK 유지!
            )

            // 4. 앱 재시작 방지 (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && dontKillApp) {
                sessionParams.setDontKillApp(true)  // ← 재시작 방지!
                Log.d(TAG, "setDontKillApp(true) - App will NOT restart")
            } else {
                Log.d(TAG, "App may restart after installation (Android < 8.0 or dontKillApp=false)")
            }

            // 5. Session 생성
            val sessionId = packageInstaller.createSession(sessionParams)
            Log.d(TAG, "Created session: $sessionId")

            val session = packageInstaller.openSession(sessionId)

            try {
                // 6. Split APK 쓰기
                val output = session.openWrite(splitName, 0, -1)
                apkFile.inputStream().use { input ->
                    output.use {
                        input.copyTo(it)
                    }
                }
                session.fsync(output)
                Log.d(TAG, "Written split APK: $splitName (${apkFile.length()} bytes)")

                // 7. 설치 커밋
                val intent = Intent(INSTALL_ACTION).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingIntent.intentSender)
                Log.d(TAG, "Installation committed")

            } catch (e: IOException) {
                session.abandon()
                throw e
            } finally {
                session.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install split APK", e)
            onResult(false, "Installation failed: ${e.message}")
        }
    }

    /**
     * 여러 Split APK 동시 설치
     */
    fun installMultipleSplits(
        splitApks: Map<String, String>,  // splitName -> apkPath
        dontKillApp: Boolean = true,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        try {
            registerInstallReceiver(onResult)

            val packageInstaller = context.packageManager.packageInstaller

            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && dontKillApp) {
                sessionParams.setDontKillApp(true)
            }

            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)

            try {
                // 여러 Split APK 쓰기
                splitApks.forEach { (splitName, apkPath) ->
                    val apkFile = File(apkPath)
                    if (!apkFile.exists()) {
                        throw IOException("APK not found: $apkPath")
                    }

                    val output = session.openWrite(splitName, 0, -1)
                    apkFile.inputStream().use { input ->
                        output.use {
                            input.copyTo(it)
                        }
                    }
                    session.fsync(output)
                    Log.d(TAG, "Written split: $splitName (${apkFile.length()} bytes)")
                }

                // 커밋
                val intent = Intent(INSTALL_ACTION).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingIntent.intentSender)
                Log.d(TAG, "Multiple splits installation committed")

            } catch (e: IOException) {
                session.abandon()
                throw e
            } finally {
                session.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install multiple splits", e)
            onResult(false, "Installation failed: ${e.message}")
        }
    }

    /**
     * InstallResultReceiver 등록
     */
    private fun registerInstallReceiver(onResult: (Boolean, String?) -> Unit) {
        // 기존 receiver 제거
        installResultReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Already unregistered
            }
        }

        // 새 receiver 등록
        installResultReceiver = InstallResultReceiver(onResult)
        val filter = IntentFilter(INSTALL_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                installResultReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(installResultReceiver, filter)
        }
    }

    /**
     * Receiver 정리
     */
    fun cleanup() {
        installResultReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Already unregistered
            }
        }
        installResultReceiver = null
    }

    /**
     * 설치 결과 수신
     */
    private class InstallResultReceiver(
        private val onResult: (Boolean, String?) -> Unit
    ) : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d(TAG, "✅ Installation successful")
                    onResult(true, "Installation successful")
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // 사용자 승인 필요
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirmIntent != null) {
                        Log.d(TAG, "⚠️ User action required")
                        try {
                            context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start user confirmation", e)
                            onResult(false, "User confirmation required but failed to show")
                        }
                    }
                }
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    Log.e(TAG, "❌ Installation failed: $message (status: $status)")
                    onResult(false, message ?: "Installation failed with status: $status")
                }
                else -> {
                    Log.w(TAG, "Unknown status: $status, message: $message")
                    onResult(false, "Unknown status: $status")
                }
            }
        }
    }
}

/**
 * 사용 예제
 */
class PackageInstallerUsageExample {

    /**
     * 예제 1: 단일 Split APK 설치 (재시작 없음)
     */
    fun installFeatureWithoutRestart(context: Context) {
        val installer = SplitApkInstaller(context)

        val splitApkPath = File(context.filesDir, "feature_camera.apk").absolutePath

        installer.installSplitApk(
            splitApkPath = splitApkPath,
            splitName = "feature_camera",
            dontKillApp = true  // ← 재시작 방지 (Android 8.0+)
        ) { success, message ->
            if (success) {
                Log.d("Example", "✅ Feature installed without restart!")

                // SplitCompat으로 즉시 로드
                com.google.android.play.core.splitcompat.SplitCompat.install(context)

                // 이제 feature 사용 가능
                val resources = context.resources
                val featureColorId = resources.getIdentifier(
                    "camera_primary", "color", context.packageName
                )
                // ...
            } else {
                Log.e("Example", "❌ Installation failed: $message")
            }

            // Cleanup
            installer.cleanup()
        }
    }

    /**
     * 예제 2: 여러 Split APK 동시 설치
     */
    fun installMultipleFeaturesWithoutRestart(context: Context) {
        val installer = SplitApkInstaller(context)

        val splits = mapOf(
            "feature_camera" to File(context.filesDir, "feature_camera.apk").absolutePath,
            "feature_ar" to File(context.filesDir, "feature_ar.apk").absolutePath,
            "config_xxhdpi" to File(context.filesDir, "config_xxhdpi.apk").absolutePath
        )

        installer.installMultipleSplits(
            splitApks = splits,
            dontKillApp = true
        ) { success, message ->
            if (success) {
                Log.d("Example", "✅ All features installed!")
                com.google.android.play.core.splitcompat.SplitCompat.install(context)
            }

            installer.cleanup()
        }
    }

    /**
     * 예제 3: Android 버전별 분기
     */
    fun installWithVersionCheck(context: Context, splitApkPath: String) {
        val installer = SplitApkInstaller(context)

        // Android 8.0+ : 재시작 없이 설치
        // Android 7.x 이하: 재시작 허용
        val dontKillApp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        installer.installSplitApk(
            splitApkPath = splitApkPath,
            splitName = "feature",
            dontKillApp = dontKillApp
        ) { success, message ->
            if (success) {
                if (dontKillApp) {
                    Log.d("Example", "✅ Installed without restart (Android 8.0+)")
                    // 즉시 사용 가능
                    com.google.android.play.core.splitcompat.SplitCompat.install(context)
                } else {
                    Log.d("Example", "✅ Installed with restart (Android < 8.0)")
                    // 앱 재시작 후 사용 가능
                }
            }

            installer.cleanup()
        }
    }
}

/**
 * 비교 데모
 */
class ComparisonDemo(private val context: Context) {

    /**
     * 방법 1: DexClassLoader (현재 프로젝트)
     */
    fun methodDexClassLoader() {
        Log.d("Demo", "=== Method 1: DexClassLoader ===")
        Log.d("Demo", "✅ No system installation")
        Log.d("Demo", "✅ No app restart (all Android versions)")
        Log.d("Demo", "⚠️ Manual resource management")
        Log.d("Demo", "✅ filesDir based")
        Log.d("Demo", "✅ No user permission needed")
    }

    /**
     * 방법 2: PackageInstaller + MODE_INHERIT_EXISTING + setDontKillApp
     */
    fun methodPackageInstallerNoRestart() {
        Log.d("Demo", "=== Method 2: PackageInstaller (No Restart) ===")
        Log.d("Demo", "✅ System installation (/data/app/)")
        Log.d("Demo", "✅ No app restart (Android 8.0+)")
        Log.d("Demo", "✅ base APK not reinstalled")
        Log.d("Demo", "✅ Unified resources (SplitCompat)")
        Log.d("Demo", "⚠️ User permission needed")
        Log.d("Demo", "⚠️ Android 8.0+ required for no-restart")
    }

    /**
     * 방법 3: PackageInstaller (재시작 허용)
     */
    fun methodPackageInstallerWithRestart() {
        Log.d("Demo", "=== Method 3: PackageInstaller (With Restart) ===")
        Log.d("Demo", "✅ System installation")
        Log.d("Demo", "⚠️ App restart (all versions)")
        Log.d("Demo", "✅ base APK not reinstalled")
        Log.d("Demo", "✅ Unified resources")
        Log.d("Demo", "⚠️ User permission needed")
        Log.d("Demo", "✅ Android 5.0+ compatible")
    }
}
