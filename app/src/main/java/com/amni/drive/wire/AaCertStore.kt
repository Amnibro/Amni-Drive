package com.amni.drive.wire

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * Manages phone-side TLS certificates for Amni Wire (Android Auto projection).
 *
 * Factory head units built on Google's receiver library validate the phone's TLS
 * certificate against the Google Automotive Link root CA. Rather than bundling
 * Google's key in the APK, users can either download the community-extracted
 * CarService certificate (pinned to the Google Automotive Link root CA) or
 * import their own PEM cert + key.
 */
object AaCertStore {
    private const val TAG = "Amni.AaCertStore"
    private const val DIR_NAME = "aa_certs"
    private const val DL_CERT = "carservice_cert.pem"
    private const val DL_KEY = "carservice_key.pem"
    private const val IMP_CERT = "imported_cert.pem"
    private const val IMP_KEY = "imported_key.pem"

    const val GOOGLE_AUTO_ROOT_SHA256 =
        "49e52efc13ad2ed09f204c3b10698bd84bb7105f510558aa14b8119a5c4ad17f"

    private const val ISCLE_BASE =
        "https://raw.githubusercontent.com/iscle/android-auto-certificates/main/android-auto/latest"
    const val ISCLE_CERT_URL = "$ISCLE_BASE/certificate.pem"
    const val ISCLE_KEY_URL = "$ISCLE_BASE/private-key.pem"

    data class CertStatus(
        val source: String,       // "IMPORTED", "DOWNLOADED", "FALLBACK"
        val subject: String,
        val notAfterMs: Long,
        val expired: Boolean,
        val googleRootVerified: Boolean
    ) {
        fun expiryLabel(): String {
            if (notAfterMs <= 0L) return "unknown"
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.format(Date(notAfterMs))
        }
    }

    private fun certDir(ctx: Context): File = File(ctx.filesDir, DIR_NAME).apply { mkdirs() }

    fun status(ctx: Context): CertStatus {
        val dir = certDir(ctx)
        val impCert = File(dir, IMP_CERT)
        val impKey = File(dir, IMP_KEY)
        if (impCert.exists() && impKey.exists()) {
            runCatching {
                val chain = parseCertChain(impCert.readBytes())
                return statusFromChain("IMPORTED", chain)
            }
        }
        val dlCert = File(dir, DL_CERT)
        val dlKey = File(dir, DL_KEY)
        if (dlCert.exists() && dlKey.exists()) {
            runCatching {
                val chain = parseCertChain(dlCert.readBytes())
                return statusFromChain("DOWNLOADED", chain)
            }
        }
        return CertStatus(
            source = "FALLBACK",
            subject = "Self-signed (amni_aa_host.p12)",
            notAfterMs = 0L,
            expired = false,
            googleRootVerified = false
        )
    }

    fun clearDownloaded(ctx: Context) {
        val dir = certDir(ctx)
        File(dir, DL_CERT).delete()
        File(dir, DL_KEY).delete()
    }

    fun clearImported(ctx: Context) {
        val dir = certDir(ctx)
        File(dir, IMP_CERT).delete()
        File(dir, IMP_KEY).delete()
    }

    /**
     * Downloads certificate.pem and private-key.pem from iscle/android-auto-certificates,
     * verifies the chain against the pinned Google Automotive Link root CA and verifies
     * the private key matches the leaf certificate before saving.
     */
    fun downloadFromIscle(ctx: Context): Result<CertStatus> = runCatching {
        val certBytes = httpGet(ISCLE_CERT_URL)
        val keyBytes = httpGet(ISCLE_KEY_URL)
        val status = validateAndParse(certBytes, keyBytes, requireGoogleRoot = true).first
        val dir = certDir(ctx)
        atomicWrite(File(dir, DL_CERT), certBytes)
        atomicWrite(File(dir, DL_KEY), keyBytes)
        Log.i(TAG, "Downloaded AA cert: subject=${status.subject} until=${status.expiryLabel()}")
        status
    }

    fun importPem(
        ctx: Context,
        certPem: ByteArray,
        keyPem: ByteArray,
        requireGoogleRoot: Boolean = false
    ): Result<CertStatus> = runCatching {
        val status = validateAndParse(certPem, keyPem, requireGoogleRoot).first
        val dir = certDir(ctx)
        atomicWrite(File(dir, IMP_CERT), certPem)
        atomicWrite(File(dir, IMP_KEY), keyPem)
        Log.i(TAG, "Imported AA cert: subject=${status.subject} until=${status.expiryLabel()}")
        status
    }

    fun importPkcs12(ctx: Context, p12Bytes: ByteArray, password: String = ""): Result<CertStatus> = runCatching {
        val ks = KeyStore.getInstance("PKCS12")
        val pass = password.toCharArray()
        ks.load(ByteArrayInputStream(p12Bytes), pass)
        val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
            ?: error("No private key entry in PKCS#12 file")
        val key = ks.getKey(alias, pass) as? PrivateKey ?: error("PKCS#12 key is not a PrivateKey")
        val chain = ks.getCertificateChain(alias)?.map { it as X509Certificate }
            ?: listOfNotNull(ks.getCertificate(alias) as? X509Certificate)
        require(chain.isNotEmpty()) { "No certificate chain in PKCS#12 file" }
        val certPem = chain.joinToString("\n") { c ->
            "-----BEGIN CERTIFICATE-----\n" +
                java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(c.encoded) +
                "\n-----END CERTIFICATE-----"
        }.toByteArray(Charsets.UTF_8)
        val keyPem = (
            "-----BEGIN PRIVATE KEY-----\n" +
                java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded) +
                "\n-----END PRIVATE KEY-----\n"
            ).toByteArray(Charsets.UTF_8)
        importPem(ctx, certPem, keyPem, requireGoogleRoot = false).getOrThrow()
    }

    @Volatile var lastAuthStatus: Int? = null
        private set
    @Volatile var preferFallbackThisBoot: Boolean = false
        private set
    fun noteAuthRefusal(status: Int) {
        lastAuthStatus = status
        if (status == 0) {
            preferFallbackThisBoot = false
        } else {
            preferFallbackThisBoot = !preferFallbackThisBoot
            runCatching { Log.w(TAG, "AuthComplete refused status=$status; preferFallbackNext=$preferFallbackThisBoot") }
        }
    }
    fun ensureReady(ctx: Context) { val st = status(ctx); if (st.source == "FALLBACK" || (st.source == "DOWNLOADED" && st.expired)) downloadFromIscle(ctx) }
    fun loadKeyManagers(ctx: Context?, log: (String) -> Unit = {}): Array<KeyManager>? {
        if (ctx == null) return null
        if (preferFallbackThisBoot) { log("TLS cert fallback active after prior auth refusal (self-signed)"); return null }
        val dir = certDir(ctx)
        val pairs = listOf(
            Triple("IMPORTED", File(dir, IMP_CERT), File(dir, IMP_KEY)),
            Triple("DOWNLOADED", File(dir, DL_CERT), File(dir, DL_KEY))
        )
        for ((label, certFile, keyFile) in pairs) {
            if (!certFile.exists() || !keyFile.exists()) continue
            try {
                val (st, chain, key) = validateAndParse(
                    certFile.readBytes(),
                    keyFile.readBytes(),
                    requireGoogleRoot = false
                )
                if (label == "DOWNLOADED" && st.expired) { log("TLS cert $label expired (${st.expiryLabel()}), skipping"); continue }
                val ks = KeyStore.getInstance("PKCS12")
                val pass = CharArray(0)
                ks.load(null, pass)
                ks.setKeyEntry("aa_phone", key, pass, chain.toTypedArray())
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, pass)
                log("TLS cert loaded source=$label subject=${st.subject} until=${st.expiryLabel()} chain=${chain.size}")
                return kmf.keyManagers
            } catch (e: Exception) {
                log("TLS cert $label failed: ${e.javaClass.simpleName}: ${e.message}")
                Log.w(TAG, "Failed loading $label cert", e)
            }
        }
        return null
    }

    internal fun validateAndParse(
        certPem: ByteArray,
        keyPem: ByteArray,
        requireGoogleRoot: Boolean
    ): Triple<CertStatus, List<X509Certificate>, PrivateKey> {
        val chain = parseCertChain(certPem)
        require(chain.isNotEmpty()) { "No X.509 certificates found in PEM" }
        val leaf = chain.first()
        val key = parsePrivateKey(keyPem)

        // Verify private key matches leaf public key via sign + verify
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sigAlg = when (key.algorithm.uppercase(Locale.US)) {
            "EC" -> "SHA256withECDSA"
            else -> "SHA256withRSA"
        }
        val sigBytes = Signature.getInstance(sigAlg).run {
            initSign(key)
            update(challenge)
            sign()
        }
        val verified = Signature.getInstance(sigAlg).run {
            initVerify(leaf.publicKey)
            update(challenge)
            verify(sigBytes)
        }
        require(verified) { "Private key does not match leaf certificate" }

        val root = chain.firstOrNull { sha256Hex(it.encoded) == GOOGLE_AUTO_ROOT_SHA256 }
        if (requireGoogleRoot) {
            requireNotNull(root) { "Certificate chain does not contain pinned Google Automotive Link root CA" }
            leaf.verify(root.publicKey)
        }

        val status = statusFromChain(if (requireGoogleRoot) "DOWNLOADED" else "IMPORTED", chain)
        return Triple(status, chain, key)
    }

    internal fun parseCertChain(pemBytes: ByteArray): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        val certs = cf.generateCertificates(ByteArrayInputStream(pemBytes))
        return certs.map { it as X509Certificate }
    }

    internal fun parsePrivateKey(pemBytes: ByteArray): PrivateKey {
        val text = String(pemBytes, Charsets.UTF_8)
        val isPkcs1Rsa = text.contains("BEGIN RSA PRIVATE KEY")
        val base64 = text
            .replace(Regex("-----BEGIN [A-Z0-9 ]+-----"), "")
            .replace(Regex("-----END [A-Z0-9 ]+-----"), "")
            .replace(Regex("\\s+"), "")
        val der = decodeBase64(base64)
        val pkcs8 = if (isPkcs1Rsa) wrapPkcs1RsaInPkcs8(der) else der
        val spec = PKCS8EncodedKeySpec(pkcs8)
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(spec)
        } catch (_: Exception) {
            KeyFactory.getInstance("EC").generatePrivate(spec)
        }
    }

    private fun decodeBase64(s: String): ByteArray {
        return try {
            Base64.decode(s, Base64.DEFAULT)
        } catch (_: Throwable) {
            // Fallback for JVM unit tests where android.util.Base64 is not mocked
            java.util.Base64.getDecoder().decode(s)
        }
    }

    /**
     * Wraps a PKCS#1 RSAPrivateKey DER blob into a PKCS#8 PrivateKeyInfo DER structure.
     */
    private fun wrapPkcs1RsaInPkcs8(pkcs1: ByteArray): ByteArray {
        // AlgorithmIdentifier for rsaEncryption (1.2.840.113549.1.1.1) + NULL
        val algId = byteArrayOf(
            0x30, 0x0d,
            0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
        )
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val octetString = derTlv(0x04, pkcs1)
        val body = version + algId + octetString
        return derTlv(0x30, body)
    }

    private fun derTlv(tag: Int, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes = when {
            len < 0x80 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun statusFromChain(source: String, chain: List<X509Certificate>): CertStatus {
        val leaf = chain.first()
        val hasRoot = chain.any { sha256Hex(it.encoded) == GOOGLE_AUTO_ROOT_SHA256 }
        val notAfter = leaf.notAfter.time
        return CertStatus(
            source = source,
            subject = leaf.subjectX500Principal.name,
            notAfterMs = notAfter,
            expired = System.currentTimeMillis() > notAfter,
            googleRootVerified = hasRoot
        )
    }

    private fun httpGet(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Amni-Drive")
        }
        try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code fetching $url" }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun atomicWrite(target: File, data: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(target)) {
            target.writeBytes(data)
            tmp.delete()
        }
    }
}
