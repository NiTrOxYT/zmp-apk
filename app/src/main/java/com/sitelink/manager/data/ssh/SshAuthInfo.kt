package com.sitelink.manager.data.ssh

import java.util.Arrays

sealed interface SshAuthInfo {
    class Password(val passwordChars: CharArray) : SshAuthInfo {
        fun clear() {
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    data class KeyStoreAuth(val alias: String = "sitelink_ssh_key") : SshAuthInfo

    class ImportedKey(
        val privateKeyBytes: ByteArray,
        val passphrase: CharArray? = null
    ) : SshAuthInfo {
        fun clear() {
            Arrays.fill(privateKeyBytes, 0.toByte())
            passphrase?.let { Arrays.fill(it, '\u0000') }
        }
    }
}
