package edu.secure.assign.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 주민등록번호 등 복호화가 필요한 데이터의 대칭 암호화 
 * GCM 모드 + 매번 새로운 IV
 * AES-GCM(128비트 키) 대칭 암호화
 * 키는 환경 변수(ASSIGN_AES_KEY)로만 주입 
 * */
@Component
public class AesCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final String base64Key;

    public AesCrypto(@Value("${assign.aes-key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);                       // 매번 새로운 IV -> 같은 평문도 매번 다른 암호문
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            // 복호화 할때 IV 가 필요하므로 IV + 암호문을 이어 붙여 저장
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(String token) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(token));
            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);
            byte[] enc = new byte[buf.remaining()];
            buf.get(enc);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    private SecretKeySpec key() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("ASSIGN_AES_KEY 환경 변수가 설정되지 않았습니다.");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }
}
