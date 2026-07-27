package com.tanda.preference.interactor

import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.common.model.Locale
import com.tanda.core.persistence.repository.PersistenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.reflect.typeOf

@Single
class LocaleInteractorDelegate(
    private val repository: PersistenceRepository,
    private val interactor: LanguageInteractor
) : LocaleInteractor {
    private val availableLocales: List<Locale>
        get() = SupportedLocale.all.map {
            Locale(
                code = it.code,
                name = it.displayName
            )
        }

    override fun default(): Locale {
        val sysLang = interactor.get()
        return availableLocales.firstOrNull { it.code == sysLang } 
            ?: availableLocales.firstOrNull { it.code == "en" }
            ?: Locale(code = "en", name = "English")
    }

    override fun current(): Locale {
        return repository.get<Model>( LOCALE_KEY, typeOf<Model>())?.let {
            Locale(
                code = it.code,
                name = it.name
            )
        } ?: default()
    }

    override fun observe(): Flow<Locale> {
        return repository.observe<Model>(LOCALE_KEY, typeOf<Model>())
            .map { model ->
                model?.let {
                    Locale(
                        code = it.code,
                        name = it.name
                    )
                } ?: default()
            }
    }

    override fun get(code: String): Locale {
        return availableLocales.firstOrNull { it.code == code } ?: default()
    }

    override fun set(code: String) {
        return repository.set(LOCALE_KEY, get(code).let {
            Model(
                code = it.code,
                name = it.name
            )
        }).apply { interactor.set(code) }
    }

    override fun supportedLanguages(): List<Locale> {
        val all = availableLocales
        val defaultLocale = default()
        val defaultIndex = all.indexOfFirst { it.code == defaultLocale.code }
        return if (defaultIndex > 0) {
            val mutableList = all.toMutableList()
            mutableList.add(0, mutableList.removeAt(defaultIndex))
            mutableList
        } else {
            all
        }
    }

    companion object {
        const val LOCALE_KEY = "locale"
    }

    @Serializable
    data class Model(
        val code: String,
        val name: String,
    )
}
