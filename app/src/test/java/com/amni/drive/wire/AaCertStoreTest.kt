package com.amni.drive.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64

class AaCertStoreTest {

    private fun loadBundledP12AsPem(): Pair<ByteArray, ByteArray> {
        val p12File = java.io.File("src/main/assets/amni_aa_host.p12")
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(ByteArrayInputStream(p12File.readBytes()), "amni_aa_research".toCharArray())
        val alias = ks.aliases().nextElement()
        val key = ks.getKey(alias, "amni_aa_research".toCharArray()) as PrivateKey
        val chain = ks.getCertificateChain(alias).map { it as X509Certificate }

        val certPem = buildString {
            for (c in chain) {
                appendLine("-----BEGIN CERTIFICATE-----")
                appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(c.encoded))
                appendLine("-----END CERTIFICATE-----")
            }
        }.toByteArray(Charsets.UTF_8)

        val keyPem = buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded))
            appendLine("-----END PRIVATE KEY-----")
        }.toByteArray(Charsets.UTF_8)

        return certPem to keyPem
    }

    @Test
    fun validateAndParse_acceptsMatchingSelfSignedWhenGoogleRootNotRequired() {
        val (certPem, keyPem) = loadBundledP12AsPem()
        val (status, chain, _) = AaCertStore.validateAndParse(certPem, keyPem, requireGoogleRoot = false)
        assertEquals("IMPORTED", status.source)
        assertFalse(status.googleRootVerified)
        assertTrue(chain.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateAndParse_rejectsSelfSignedWhenGoogleRootRequired() {
        val (certPem, keyPem) = loadBundledP12AsPem()
        AaCertStore.validateAndParse(certPem, keyPem, requireGoogleRoot = true)
    }
}
