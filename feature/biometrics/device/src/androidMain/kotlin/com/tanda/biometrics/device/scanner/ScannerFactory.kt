package com.tanda.biometrics.device.scanner

import android.content.Context
import com.integratedbiometrics.ibscanultimate.IBScan
import com.tanda.biometrics.domain.exception.ScannerNotFoundException

interface ScannerFactory {
    fun create(context: Context): IBScan

    class Delegate : ScannerFactory {
        override fun create(context: Context): IBScan {
            return try {
                IBScan.getInstance(context)
            } catch (e: Throwable) {
                throw ScannerNotFoundException(e)
            }
        }
    }
}