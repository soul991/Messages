package com.personal.detectivedialer.data.remote

import com.personal.detectivedialer.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds the shared-secret `X-Api-Key` header when the user has configured one.
 * When blank, requests go out unchanged (matches the backend, which only
 * enforces the key when its API_KEY env is set).
 */
@Singleton
class ApiKeyInterceptor @Inject constructor(
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // runBlocking is safe here: interceptors run on OkHttp worker threads,
        // and DataStore serves reads from its in-memory cache after the first.
        val key = runBlocking { settings.settings.first().apiKey }.trim()
        val request = if (key.isEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("X-Api-Key", key).build()
        }
        return chain.proceed(request)
    }
}
