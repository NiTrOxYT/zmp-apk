package com.sitelink.manager.data.config

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerConfigManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)

        override fun getString(key: String?, defValue: String?): String? {
            return (map[key] as? String) ?: defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            return (map[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return (map[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return (map[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return (map[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return (map[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) changes[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) changes[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) changes[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) changes[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) changes[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) changes[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removals.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    prefs.map.clear()
                }
                for (rem in removals) {
                    prefs.map.remove(rem)
                }
                prefs.map.putAll(changes)
            }
        }
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var configManager: ServerConfigManager

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        configManager = ServerConfigManager(customPrefs = fakePrefs)
    }

    @Test
    fun testDefaultOfficeServerConfiguration() {
        // Must default to specified office server: 111.93.205.187:22, sourik, s0urik@@
        assertEquals("111.93.205.187", configManager.getHost())
        assertEquals(22, configManager.getPort())
        assertEquals("sourik", configManager.getUsername())
        assertEquals("s0urik@@", configManager.getPassword())
        assertFalse("autoAcceptHostKey must be false by default for security", configManager.isAutoAcceptHostKey())
        assertTrue("hasSavedPassword must be true initially with default credential", configManager.hasSavedPassword())
    }

    @Test
    fun testSaveAndRetrieveCredentials() {
        configManager.saveConfig(
            host = "111.93.205.187",
            port = 22,
            username = "sourik",
            password = "testSecretPassword123!",
            autoAcceptHostKey = false
        )

        assertEquals("111.93.205.187", configManager.getHost())
        assertEquals(22, configManager.getPort())
        assertEquals("sourik", configManager.getUsername())
        assertEquals("testSecretPassword123!", configManager.getPassword())
        assertTrue("hasSavedPassword must be true once password is saved", configManager.hasSavedPassword())
        assertFalse("Strict host verification must be enforced", configManager.isAutoAcceptHostKey())

        // Recreating manager from same prefs must restore saved state
        val reloadedManager = ServerConfigManager(customPrefs = fakePrefs)
        assertEquals("111.93.205.187", reloadedManager.getHost())
        assertEquals("sourik", reloadedManager.getUsername())
        assertEquals("testSecretPassword123!", reloadedManager.getPassword())
        assertTrue(reloadedManager.hasSavedPassword())
    }

    @Test
    fun testUpdateSavedPassword() {
        configManager.saveConfig(
            host = "111.93.205.187",
            port = 22,
            username = "sourik",
            password = "oldPassword",
            autoAcceptHostKey = false
        )
        assertTrue(configManager.hasSavedPassword())

        // Update password (e.g. after password change on server)
        configManager.updatePassword("newRotatedPassword456$")

        assertEquals("newRotatedPassword456$", configManager.getPassword())
        assertEquals("111.93.205.187", configManager.getHost())
        assertEquals("sourik", configManager.getUsername())
        assertTrue(configManager.hasSavedPassword())

        // Verify persisted in prefs
        val reloadedManager = ServerConfigManager(customPrefs = fakePrefs)
        assertEquals("newRotatedPassword456$", reloadedManager.getPassword())
    }

    @Test
    fun testDeleteSavedCredentials() {
        configManager.saveConfig(
            host = "111.93.205.187",
            port = 22,
            username = "sourik",
            password = "mySecretPassword",
            autoAcceptHostKey = false
        )
        assertTrue(configManager.hasSavedPassword())

        // Delete saved credentials
        configManager.deleteSavedCredentials()

        assertEquals("", configManager.getPassword())
        assertFalse("hasSavedPassword must be false after deletion", configManager.hasSavedPassword())

        // Host and username should remain preserved for convenience
        assertEquals("111.93.205.187", configManager.getHost())
        assertEquals(22, configManager.getPort())
        assertEquals("sourik", configManager.getUsername())

        // Verify persisted deletion
        val reloadedManager = ServerConfigManager(customPrefs = fakePrefs)
        assertEquals("", reloadedManager.getPassword())
        assertFalse(reloadedManager.hasSavedPassword())
    }

    @Test
    fun testWhitespaceTrimmingOnSave() {
        configManager.saveConfig(
            host = "  111.93.205.187  ",
            port = 22,
            username = "  sourik  ",
            password = "secretPassword",
            autoAcceptHostKey = false
        )

        assertEquals("111.93.205.187", configManager.getHost())
        assertEquals("sourik", configManager.getUsername())
        assertEquals("secretPassword", configManager.getPassword())
    }
}
