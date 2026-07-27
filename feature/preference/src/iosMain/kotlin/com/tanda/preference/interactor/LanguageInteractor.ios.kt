package com.tanda.preference.interactor

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual class LanguageInteractor {
    actual fun get(): String {
        return NSLocale.currentLocale.languageCode
    }

    actual fun set(code: String) {
        platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(
            arrayListOf(code),
            "AppleLanguages"
        )
        platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
    }
}

actual fun getLanguageInteractor(): LanguageInteractor {
    return LanguageInteractor()
}
