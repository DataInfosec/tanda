package com.tanda.core.common.interactor

import com.tanda.core.common.model.Locale
import kotlinx.coroutines.flow.Flow

interface LocaleInteractor {
    fun default(): Locale

    fun current(): Locale

    fun observe(): Flow<Locale>

    fun get(code: String): Locale

    fun set(code: String)

    fun supportedLanguages(): List<Locale>
}
