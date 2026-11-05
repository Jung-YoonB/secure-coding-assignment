package edu.secure.assign.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 요청 본문 항목별 허용 형식만 통과 (허용 목록 방식) */
public record SignupRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{4,10}$")
        String customerId,

        @NotBlank
        @Pattern(regexp = "^[가-힣A-Za-z ]{2,16}$")
        String customerName,

        @NotBlank
        @Pattern(regexp = "^\\d{6}-\\d{7}$")
        String rrn,

        @NotBlank
        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$")
        String phone,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
        String password) {
}