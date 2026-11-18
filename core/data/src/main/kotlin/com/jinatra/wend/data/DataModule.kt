package com.jinatra.wend.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): WendDatabase = EncryptedDatabaseFactory.open(context)

    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
