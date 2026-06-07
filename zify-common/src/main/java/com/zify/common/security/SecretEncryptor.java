package com.zify.common.security;

import com.zify.common.config.properties.SecurityProperties;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts and decrypts sensitive values.
 */
@Component
public class SecretEncryptor {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TEXT_PREFIX = "v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptor(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return CIPHER_TEXT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to encrypt secret", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        if (!cipherText.startsWith(CIPHER_TEXT_PREFIX)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid cipher text format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText.substring(CIPHER_TEXT_PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid cipher text payload");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid cipher text encoding", e);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to decrypt secret", e);
        }
    }

    private SecretKeySpec secretKey() {
        String encryptionKey = securityProperties.getEncryptionKey();
        if (!StringUtils.hasText(encryptionKey)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "zify.security.encryption-key is not configured");
        }
        return new SecretKeySpec(deriveKey(encryptionKey), KEY_ALGORITHM);
    }

    private byte[] deriveKey(String encryptionKey) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to derive encryption key", e);
        }
    }
}
