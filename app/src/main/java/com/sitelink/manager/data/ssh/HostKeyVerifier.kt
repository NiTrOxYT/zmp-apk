package com.sitelink.manager.data.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import com.sitelink.manager.data.db.TrustedHostKeyDao
import com.sitelink.manager.data.db.TrustedHostKeyEntity
import com.sitelink.manager.domain.model.HostVerificationResult
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.Base64

class CustomHostKeyRepository(
    private val trustedHostKeyDao: TrustedHostKeyDao,
    private val onVerificationRequired: (HostVerificationResult) -> Unit
) : HostKeyRepository {

    override fun check(host: String?, key: ByteArray?): Int {
        if (host == null || key == null) {
            return HostKeyRepository.NOT_INCLUDED
        }

        val calculatedFingerprint = calculateSha256Fingerprint(key)
        val variants = normalizeHostVariants(host)

        val trustedEntity = runBlocking {
            var found: TrustedHostKeyEntity? = null
            for (v in variants) {
                found = trustedHostKeyDao.getTrustedKey(v)
                if (found != null) break
            }
            found
        }

        if (trustedEntity == null) {
            val keyType = guessKeyType(key)
            val port = parsePortFromHost(host)
            onVerificationRequired(
                HostVerificationResult.UnknownHost(
                    host = variants.firstOrNull() ?: host,
                    port = port,
                    keyType = keyType,
                    fingerprint = calculatedFingerprint,
                    rawKey = key
                )
            )
            return HostKeyRepository.NOT_INCLUDED
        }

        if (trustedEntity.fingerprint == calculatedFingerprint) {
            return HostKeyRepository.OK
        } else {
            // Hard security rejection: Host key has changed! (Potential MITM)
            val port = parsePortFromHost(host)
            onVerificationRequired(
                HostVerificationResult.KeyChanged(
                    host = variants.firstOrNull() ?: host,
                    port = port,
                    keyType = trustedEntity.keyType,
                    newFingerprint = calculatedFingerprint,
                    oldFingerprint = trustedEntity.fingerprint
                )
            )
            return HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey?, userinfo: UserInfo?) {
        if (hostkey == null) return
        val host = hostkey.host ?: return
        val key = hostkey.key ?: return
        val keyBytes = try {
            Base64.getMimeDecoder().decode(key)
        } catch (_: Exception) {
            key.toByteArray(Charsets.US_ASCII)
        }
        val fp = calculateSha256Fingerprint(keyBytes)
        val cleanHost = host.trim().removeSurrounding("[", "]").lowercase()

        runBlocking {
            trustedHostKeyDao.insertOrUpdate(
                TrustedHostKeyEntity(
                    hostPortKey = cleanHost,
                    host = cleanHost,
                    port = parsePortFromHost(cleanHost),
                    keyType = hostkey.type ?: guessKeyType(keyBytes),
                    fingerprint = fp,
                    rawKeyBase64 = key
                )
            )
        }
    }

    override fun remove(host: String?, type: String?) {
        if (host == null) return
        val variants = normalizeHostVariants(host)
        runBlocking {
            for (v in variants) {
                trustedHostKeyDao.deleteTrustedKey(v)
            }
        }
    }

    override fun remove(host: String?, type: String?, key: ByteArray?) {
        remove(host, type)
    }

    override fun getKnownHostsRepositoryID(): String = "SiteLinkDatabaseHostKeyRepository"

    override fun getHostKey(): Array<HostKey> {
        return runBlocking {
            val allEntities = trustedHostKeyDao.getAllTrustedKeysList()
            allEntities.mapNotNull { entityToHostKey(it) }.toTypedArray()
        }
    }

    /**
     * Handles JSch host-key queries when type is specific OR null.
     * JSch passes type = null when querying any known key for host.
     */
    override fun getHostKey(host: String?, type: String?): Array<HostKey> {
        if (host == null) return emptyArray()

        val variants = normalizeHostVariants(host)
        return runBlocking {
            var foundEntity: TrustedHostKeyEntity? = null
            for (v in variants) {
                foundEntity = trustedHostKeyDao.getTrustedKey(v)
                if (foundEntity != null) break
            }

            if (foundEntity != null) {
                if (type == null || isKeyTypeMatched(foundEntity.keyType, type)) {
                    val hk = entityToHostKey(foundEntity)
                    if (hk != null) arrayOf(hk) else emptyArray()
                } else {
                    emptyArray()
                }
            } else {
                emptyArray()
            }
        }
    }

    private fun entityToHostKey(entity: TrustedHostKeyEntity): HostKey? {
        return try {
            val keyBytes = Base64.getMimeDecoder().decode(entity.rawKeyBase64)
            val hkType = parseHostKeyType(entity.keyType)
            HostKey(entity.host, hkType, keyBytes)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun calculateSha256Fingerprint(keyBytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(keyBytes)
            val base64 = Base64.getEncoder().withoutPadding().encodeToString(digest)
            return "SHA256:$base64"
        }

        fun guessKeyType(keyBytes: ByteArray): String {
            return try {
                if (keyBytes.size > 11) {
                    val len = (keyBytes[0].toInt() and 0xFF shl 24) or
                            (keyBytes[1].toInt() and 0xFF shl 16) or
                            (keyBytes[2].toInt() and 0xFF shl 8) or
                            (keyBytes[3].toInt() and 0xFF)
                    if (len in 1..50 && len + 4 <= keyBytes.size) {
                        String(keyBytes, 4, len, Charsets.US_ASCII)
                    } else "ssh-rsa"
                } else "ssh-rsa"
            } catch (e: Exception) {
                "ssh-rsa"
            }
        }

        fun parseHostKeyType(typeStr: String?): Int {
            if (typeStr == null) return HostKey.GUESS
            val lower = typeStr.lowercase()
            return when {
                lower.contains("ecdsa-sha2-nistp256") -> HostKey.ECDSA256
                lower.contains("ecdsa-sha2-nistp384") -> HostKey.ECDSA384
                lower.contains("ecdsa-sha2-nistp521") -> HostKey.ECDSA521
                lower.contains("dss") -> HostKey.SSHDSS
                lower.contains("rsa") -> HostKey.SSHRSA
                else -> HostKey.GUESS
            }
        }

        fun isKeyTypeMatched(storedType: String, requestedType: String?): Boolean {
            if (requestedType == null) return true
            if (storedType.equals(requestedType, ignoreCase = true)) return true
            val lowerStored = storedType.lowercase()
            val lowerReq = requestedType.lowercase()
            return lowerStored.contains(lowerReq) || lowerReq.contains(lowerStored)
        }

        fun normalizeHostVariants(host: String): List<String> {
            val clean = host.trim().removeSurrounding("[", "]").lowercase()
            val variants = mutableListOf(clean)
            if (clean.contains(":")) {
                val withoutPort = clean.substringBefore(":")
                variants.add(withoutPort)
            } else {
                variants.add("$clean:22")
            }
            return variants.distinct()
        }

        fun parsePortFromHost(host: String): Int {
            val clean = host.trim().removeSurrounding("[", "]")
            if (clean.contains(":")) {
                return clean.substringAfter(":").toIntOrNull() ?: 22
            }
            return 22
        }
    }
}
