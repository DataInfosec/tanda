package com.tanda.preference.interactor

expect class LanguageInteractor {
    fun get(): String
    fun set(code: String)
}

expect fun getLanguageInteractor(): LanguageInteractor
