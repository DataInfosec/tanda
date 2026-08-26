package com.tanda.biometrics.device.scanner

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import com.tanda.biometrics.device.interactor.ScannerInteractor
import com.tanda.core.persistence.usecase.SetBooleanUsecase
import org.koin.core.scope.Scope

class ScannerService : Service() {
    private lateinit var setter: SetBooleanUsecase

    private lateinit var interactor: ScannerInteractor

    private val fingerConfigReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val enabled = intent.getBooleanExtra("enable", false)
            setter(
                SetBooleanUsecase.Argument(
                    key = KEY,
                    value = enabled
                )
            )
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        val scope = (application as Provider).scope
        setter = scope.get()
        interactor = scope.get()
        val enable = try {
            interactor.isActive()
        } catch (error: Throwable) {
            error.printStackTrace()
            false
        }
        val filter = IntentFilter("mtk.intent.ACTION_FINGER_CONFIG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fingerConfigReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fingerConfigReceiver, filter)
        }
        setter(
            SetBooleanUsecase.Argument(
                key = KEY,
                value = enable
            )
        )
    }

    override fun onDestroy() {
        unregisterReceiver(fingerConfigReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    interface Provider {
        val scope: Scope
    }

    companion object {
        internal const val KEY = "finger_config_enabled"
    }
}
