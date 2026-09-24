package com.maximarcana.securecc.net;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated-encryption core for the secure modem/cable network.
 *
 * Every secure modem derives its AES-128 key deterministically from its
 * owner's UUID:
 *
 *   key = SHA-256("SecureCC:modem:v1:" + ownerUUID)[0..16]
 *
 * Because the key is owner-derived, all of one player's secure modems can
 * talk to each other with no key exchange, while a different owner's modem
 * (or any vanilla modem) cannot decrypt or forge messages. Decryption
 * failure is the authentication check: only same-owner packets decode.
 */
public final class SecureCrypto {
    private SecureCrypto() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 16;

    /** Deterministic 128-bit AES key for this owner. */
    public static byte[] deriveKey(UUID ownerId) {
        if (ownerId == null) throw new IllegalArgumentException("ownerId is null");
        byte[] hash = sha256(("SecureCC:modem:v1:" + ownerId.toString())
                .getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(hash, 0, key, 0, 16);
        return key;
    }

    /**
     * Encrypt a modem message. Returns Base64(IV || ciphertext), or null on
     * failure. The reply channel is bundled into the plaintext so it cannot
     * be tampered with independently.
     */
    public static String encrypt(UUID ownerId, int replyChannel, String message) {
        try {
            byte[] key = deriveKey(ownerId);
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            String plaintext = replyChannel + "\n" + (message == null ? "" : message);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, out, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypt a modem message. Returns a two-element array
     * [replyChannel, message], or null when decryption fails (wrong owner,
     * corrupt data, or tampering).
     */
    public static String[] decrypt(UUID ownerId, String encrypted) {
        try {
            byte[] key = deriveKey(ownerId);
            byte[] data = Base64.getDecoder().decode(encrypted);
            if (data.length <= IV_BYTES) return null;
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[data.length - IV_BYTES];
            System.arraycopy(data, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            String plaintext = new String(cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8);
            int nl = plaintext.indexOf('\n');
            if (nl < 0) return null;
            // Validate the reply channel is a sane integer.
            Integer.parseInt(plaintext.substring(0, nl));
            return new String[]{plaintext.substring(0, nl),
                    plaintext.substring(nl + 1)};
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
