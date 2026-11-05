package edu.secure.assign.security;

/** 마스킹 유틸. 응답 전용 객체를 만들 때 활용한다. */
public final class MaskingUtil {

    private MaskingUtil() {
    }

    /** 900101-1234567 → 900101-1****** */
    public static String maskRrn(String rrn) {
        if (rrn == null || rrn.length() < 8 || rrn.indexOf('-') != 6) {
            return "******-*******";
        }
        return rrn.substring(0, 8) + "*".repeat(rrn.length() - 8);
    }

    /** 010-1111-2222 → 010-****-2222 */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String[] p = phone.split("-");
        if (p.length != 3) {
            return "***";
        }
        return p[0] + "-" + "*".repeat(p[1].length()) + "-" + p[2];
    }
}
