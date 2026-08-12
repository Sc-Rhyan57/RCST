package com.rhyan57.rcst

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhyan57.rcst.ui.components.CrashScreen
import com.rhyan57.rcst.ui.screens.MainScreen
import com.rhyan57.rcst.ui.theme.AppTheme
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_CRASH    = "rcst_crash"
        private const val KEY_CRASH_LOG = "crash_trace"
    }

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCrashHandler()

        val crashPrefs = getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
        val crashTrace = crashPrefs.getString(KEY_CRASH_LOG, null)
        if (crashTrace != null) {
            crashPrefs.edit().remove(KEY_CRASH_LOG).apply()
        }

        if (crashTrace == null) {
            vm = ViewModelProvider(this)[MainViewModel::class.java]
        }

        enableEdgeToEdge()
        setContent {
            if (crashTrace != null) {
                AppTheme {
                    CrashScreen(trace = crashTrace)
                }
            } else {
                val themeMode by vm.themeMode.collectAsStateWithLifecycle()
                val materialYou by vm.materialYou.collectAsStateWithLifecycle()
                AppTheme(themeMode = themeMode, materialYou = materialYou) {
                    MainScreen(vm = vm)
                }
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val log = "RCST - Crash Report\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\n\nStacktrace:\n$sw"
                getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH_LOG, log)
                    .commit()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(t, e)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(2)
        }
    }
}
