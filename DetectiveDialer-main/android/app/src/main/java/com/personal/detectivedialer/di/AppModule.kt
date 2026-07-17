package com.personal.detectivedialer.di

import android.content.Context
import androidx.room.Room
import com.personal.detectivedialer.BuildConfig
import com.personal.detectivedialer.data.local.AllowedNumberDao
import com.personal.detectivedialer.data.local.AppDatabase
import com.personal.detectivedialer.data.local.BlockedNumberDao
import com.personal.detectivedialer.data.local.CallLogDao
import com.personal.detectivedialer.data.local.SmsDao
import com.personal.detectivedialer.data.local.SmsSenderPrefDao
import com.personal.detectivedialer.data.remote.ApiKeyInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** OkHttp client tuned for the /screen call: must answer inside the ~5s CallScreeningService budget. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScreenClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBlockedDao(db: AppDatabase): BlockedNumberDao = db.blockedNumberDao()

    @Provides
    fun provideAllowedDao(db: AppDatabase): AllowedNumberDao = db.allowedNumberDao()

    @Provides
    fun provideCallLogDao(db: AppDatabase): CallLogDao = db.callLogDao()

    @Provides
    fun provideSmsDao(db: AppDatabase): SmsDao = db.smsDao()

    @Provides
    fun provideSmsSenderPrefDao(db: AppDatabase): SmsSenderPrefDao = db.smsSenderPrefDao()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun baseClient(apiKeyInterceptor: ApiKeyInterceptor): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().addInterceptor(apiKeyInterceptor)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder
    }

    /** Default client for history/list sync — generous timeouts are fine here. */
    @Provides
    @Singleton
    fun provideOkHttp(apiKeyInterceptor: ApiKeyInterceptor): OkHttpClient =
        baseClient(apiKeyInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    /**
     * Screening client: the OS gives CallScreeningService roughly 5 seconds to
     * respond, so the whole /screen request must fail fast and let the service
     * fall back to ALLOW instead of blowing the deadline.
     */
    @Provides
    @Singleton
    @ScreenClient
    fun provideScreenOkHttp(apiKeyInterceptor: ApiKeyInterceptor): OkHttpClient =
        baseClient(apiKeyInterceptor)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
}
