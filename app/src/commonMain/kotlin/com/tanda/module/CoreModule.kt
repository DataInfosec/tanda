package com.tanda.module

import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class CoreModule {
    @Single
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Single
    fun provideDispatcher(): Dispatcher = object : Dispatcher {
        override val io: CoroutineDispatcher get() = Dispatchers.IO
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val default: CoroutineDispatcher get() = Dispatchers.Default
    }
}
