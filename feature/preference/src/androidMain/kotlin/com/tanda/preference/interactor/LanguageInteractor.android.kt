package com.tanda.preference.interactor

import java.util.Locale

actual class LanguageInteractor {
    actual fun get(): String {
        return Locale.getDefault().language
    }

    actual fun set(code: String) {
        Locale.setDefault(Locale(code))
    }
}

actual fun getLanguageInteractor(): LanguageInteractor {
    return LanguageInteractor()
}
