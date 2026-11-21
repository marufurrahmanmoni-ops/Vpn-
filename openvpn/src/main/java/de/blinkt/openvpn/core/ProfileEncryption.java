/*
 * Free Global VPN – replacement for the ics-openvpn "skeleton" ProfileEncryption stub.
 * Distributed under the GNU GPL v2 (same as ics-openvpn).
 *
 * Stores every saved VPN profile (which contains certificates, keys and credentials)
 * encrypted with AES-256-GCM. The key lives in the Android Keystore, is hardware-backed
 * where the device supports it, and never leaves the Keystore.
 *
 * File layout: [12-byte IV][ciphertext + 16-byte GCM tag]
 */
package de.blinkt.openvpn.core;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

class ProfileEncryption {
    private static final String KEY_ALIAS = "fgv_profile_key_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private static volatile SecretKey sKey;

    static void initMasterCryptAlias(Context context) {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                kg.generateKey();
            }
            sKey = (SecretKey) ks.getKey(KEY_ALIAS, null);
        } catch (Exception e) {
            sKey = null;
            VpnStatus.logException("Could not initialise profile encryption key", e);
        }
    }

    static boolean encryptionEnabled() {
        return sKey != null;
    }

    static FileInputStream getEncryptedVpInput(Context context, File file) throws GeneralSecurityException, IOException {
        if (sKey == null) throw new GeneralSecurityException("No profile key");
        byte[] all;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            all = bos.toByteArray();
        }
        if (all.length <= IV_LEN) throw new IOException("Encrypted profile too short");
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.DECRYPT_MODE, sKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
        byte[] plain = c.doFinal(all, IV_LEN, all.length - IV_LEN);
        return new DecryptedInputStream(file, plain);
    }

    static FileOutputStream getEncryptedVpOutput(Context context, File file) throws GeneralSecurityException, IOException {
        if (sKey == null) throw new GeneralSecurityException("No profile key");
        return new EncryptingOutputStream(file, sKey);
    }

    /** FileInputStream (required by the caller's signature) that serves decrypted bytes from memory. */
    private static final class DecryptedInputStream extends FileInputStream {
        private final ByteArrayInputStream data;

        DecryptedInputStream(File file, byte[] plain) throws IOException {
            super(file);
            data = new ByteArrayInputStream(plain);
        }

        @Override public int read() { return data.read(); }
        @Override public int read(byte[] b) { return data.read(b, 0, b.length); }
        @Override public int read(byte[] b, int off, int len) { return data.read(b, off, len); }
        @Override public long skip(long n) { return data.skip(n); }
        @Override public int available() { return data.available(); }
    }

    /** Buffers plaintext and writes IV + AES-GCM ciphertext to the file on close(). */
    private static final class EncryptingOutputStream extends FileOutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final SecretKey key;
        private boolean closed = false;

        EncryptingOutputStream(File file, SecretKey key) throws IOException {
            super(file);
            this.key = key;
        }

        @Override public void write(int b) { buffer.write(b); }
        @Override public void write(byte[] b) { buffer.write(b, 0, b.length); }
        @Override public void write(byte[] b, int off, int len) { buffer.write(b, off, len); }
        @Override public void flush() { /* data is written on close */ }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                Cipher c = Cipher.getInstance(TRANSFORMATION);
                c.init(Cipher.ENCRYPT_MODE, key);
                byte[] iv = c.getIV();
                byte[] enc = c.doFinal(buffer.toByteArray());
                super.write(iv, 0, iv.length);
                super.write(enc, 0, enc.length);
                super.flush();
            } catch (GeneralSecurityException e) {
                throw new IOException("Profile encryption failed", e);
            } finally {
                super.close();
            }
        }
    }
}
