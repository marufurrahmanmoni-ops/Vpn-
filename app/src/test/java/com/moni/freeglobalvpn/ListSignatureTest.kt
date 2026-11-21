package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.provider.ListSignature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class ListSignatureTest {
    @Test fun acceptsOnlyUntamperedSignedList() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        val data = """{"servers":[{"ip":"1.2.3.4"}]}""".toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(data); Base64.getEncoder().encodeToString(sign()) }
        assertTrue(ListSignature.verify(data, sig, pub))
        assertFalse("tampered list", ListSignature.verify("""{"servers":[{"ip":"6.6.6.6"}]}""".toByteArray(), sig, pub))
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertFalse("wrong key", ListSignature.verify(data, sig, Base64.getEncoder().encodeToString(other.public.encoded)))
        assertFalse("garbage", ListSignature.verify(data, "not-base64!", pub))
    }
}
