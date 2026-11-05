package edu.secure.assign.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 비밀번호 저장/검증 : 무작위 솔트 + PBKDF2(느린 해시). 저장 형식 salt:hash (hex) */
public final class PasswordHasher {

    private static final int ITERATIONS = 600_000;   // 실무에서는 최신 권고 기준을 확인해 적용, 반복 횟수가 클수록 무차별 대입이 느려짐
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }


    /** [안전] 무작위 솔트 + PBKDF2. 저장 형식: salt:hash (hex) */
    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);				// 사용자마다, 매번 다른 솔트
        return HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(derive(password, salt));
    }

    public static boolean verify(String password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = HexFormat.of().parseHex(parts[0]);
        byte[] expected = HexFormat.of().parseHex(parts[1]);
        return MessageDigest.isEqual(derive(password, salt), expected);   // 상수 시간 비교
    }

    private static byte[] derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }
}
