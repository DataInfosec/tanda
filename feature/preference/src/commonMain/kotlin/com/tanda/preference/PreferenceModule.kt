package com.tanda.preference

import com.tanda.preference.interactor.LanguageInteractor
import com.tanda.preference.interactor.getLanguageInteractor
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan(value = [
    "com.tanda.preference.delegate",
    "com.tanda.preference.interactor"
])
class PreferenceModule {
    @Single
    fun provideLanguageInteractor(): LanguageInteractor {
        return getLanguageInteractor()
    }
}
