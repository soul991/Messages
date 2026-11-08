package com.messages.app

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesMigrationTest {

    private class InMemoryPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            map[key]?.toString() ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            (map[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            (map[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            (map[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                pending.clear()
                return this
            }
            override fun commit(): Boolean {
                map.putAll(pending)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    private class MockContext(private val prefs: SharedPreferences) : android.content.ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefs
        }
        override fun getApplicationContext(): Context {
            return this
        }
    }

    @Test
    fun `light launcher icon default is disabled`() {
        val prefs = InMemoryPreferences()
        val context = MockContext(prefs)
        assertFalse(ThemePreferences.isLightIconEnabled(context))
    }

    @Test
    fun `light launcher icon enabled state is persisted`() {
        val prefs = InMemoryPreferences().apply {
            map["light_launcher_icon"] = true
        }
        val context = MockContext(prefs)
        assertTrue(ThemePreferences.isLightIconEnabled(context))
    }
}
