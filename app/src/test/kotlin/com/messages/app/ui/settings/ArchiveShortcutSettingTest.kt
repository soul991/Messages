package com.messages.app.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.messages.app.ui.home.SwipeActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveShortcutSettingTest {

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
                if (key != null) pending[key] = null
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

    private class MockContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun `unconfigured default has null shortcut and both swipe directions disabled`() {
        val prefs = InMemoryPreferences()
        val ctx = MockContext(prefs)

        SwipeActions.init(ctx)

        assertNull("Shortcut must default to null when unchosen", SwipeActions.archiveShortcut.value)
        assertEquals("Right swipe must do nothing when unchosen", SwipeActions.NONE, SwipeActions.right.value)
        assertEquals("Left swipe must do nothing when unchosen", SwipeActions.NONE, SwipeActions.left.value)
    }

    @Test
    fun `swipe right enables right archive and disables left`() {
        val prefs = InMemoryPreferences()
        val ctx = MockContext(prefs)

        SwipeActions.setArchiveShortcut(ctx, SwipeActions.SHORTCUT_SWIPE_RIGHT)

        assertEquals(SwipeActions.SHORTCUT_SWIPE_RIGHT, SwipeActions.archiveShortcut.value)
        assertEquals(SwipeActions.ARCHIVE, SwipeActions.right.value)
        assertEquals(SwipeActions.NONE, SwipeActions.left.value)
        assertEquals(SwipeActions.SHORTCUT_SWIPE_RIGHT, prefs.getString(SwipeActions.KEY_ARCHIVE_SHORTCUT, null))
    }

    @Test
    fun `swipe left enables left archive and disables right`() {
        val prefs = InMemoryPreferences()
        val ctx = MockContext(prefs)

        SwipeActions.setArchiveShortcut(ctx, SwipeActions.SHORTCUT_SWIPE_LEFT)

        assertEquals(SwipeActions.SHORTCUT_SWIPE_LEFT, SwipeActions.archiveShortcut.value)
        assertEquals(SwipeActions.NONE, SwipeActions.right.value)
        assertEquals(SwipeActions.ARCHIVE, SwipeActions.left.value)
        assertEquals(SwipeActions.SHORTCUT_SWIPE_LEFT, prefs.getString(SwipeActions.KEY_ARCHIVE_SHORTCUT, null))
    }

    @Test
    fun `off disables both swipe directions`() {
        val prefs = InMemoryPreferences()
        val ctx = MockContext(prefs)

        SwipeActions.setArchiveShortcut(ctx, SwipeActions.SHORTCUT_OFF)

        assertEquals(SwipeActions.SHORTCUT_OFF, SwipeActions.archiveShortcut.value)
        assertEquals(SwipeActions.NONE, SwipeActions.right.value)
        assertEquals(SwipeActions.NONE, SwipeActions.left.value)
        assertEquals(SwipeActions.SHORTCUT_OFF, prefs.getString(SwipeActions.KEY_ARCHIVE_SHORTCUT, null))
    }

    @Test
    fun `legacy non-archive preferences migrate cleanly to off`() {
        val prefs = InMemoryPreferences()
        prefs.map["swipe_right"] = "pin"
        val ctx = MockContext(prefs)

        SwipeActions.init(ctx)

        assertEquals(SwipeActions.SHORTCUT_OFF, SwipeActions.archiveShortcut.value)
        assertEquals(SwipeActions.NONE, SwipeActions.right.value)
        assertEquals(SwipeActions.NONE, SwipeActions.left.value)
        assertEquals(SwipeActions.SHORTCUT_OFF, prefs.getString(SwipeActions.KEY_ARCHIVE_SHORTCUT, null))
    }
}
