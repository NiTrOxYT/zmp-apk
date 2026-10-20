package com.sitelink.manager.data.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey

class KeyStoreManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "sitelink_ssh_key"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Checks if a hardware-backed key pair already exists.
     */
    fun hasKeyPair(alias: String = DEFAULT_KEY_ALIAS): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * Generates a new Keystore-protected RSA 2048-bit key pair.
     */
    fun generateRsaKeyPair(alias: String = DEFAULT_KEY_ALIAS): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(2048)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Retrieves the existing KeyPair or null.
     */
    fun getKeyPair(alias: String = DEFAULT_KEY_ALIAS): KeyPair? {
        if (!keyStore.containsAlias(alias)) return null
        val cert = keyStore.getCertificate(alias) ?: return null
        val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey ?: return null
        return KeyPair(cert.publicKey, privateKey)
    }

    /**
     * Exports the public key in standard OpenSSH format:
     * "ssh-rsa <Base64> sitelink-manager@android"
     */
    fun getOpenSshPublicKey(alias: String = DEFAULT_KEY_ALIAS): String? {
        val cert = keyStore.getCertificate(alias) ?: return null
        val pubKey = cert.publicKey as? RSAPublicKey ?: return null

        val byteStream = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteStream)

        // "ssh-rsa"
        val header = "ssh-rsa".toByteArray(Charsets.US_ASCII)
        dataOut.writeInt(header.size)
        dataOut.write(header)

        // exponent (e)
        val expBytes = pubKey.publicExponent.toByteArray()
        dataOut.writeInt(expBytes.size)
        dataOut.write(expBytes)

        // modulus (n)
        val modBytes = pubKey.modulus.toByteArray()
        dataOut.writeInt(modBytes.size)
        dataOut.write(modBytes)

        val base64Key = Base64.getEncoder().encodeToString(byteStream.toByteArray())
        return "ssh-rsa $base64Key sitelink-manager@android"
    }

    /**
     * Deletes the key from Keystore.
     */
    fun deleteKey(alias: String = DEFAULT_KEY_ALIAS) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
