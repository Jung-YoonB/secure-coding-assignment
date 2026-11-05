package edu.secure.assign;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.SignupRequest;
import edu.secure.assign.security.AesCrypto;
import edu.secure.assign.security.PasswordHasher;
import edu.secure.assign.service.LoanService;
import edu.secure.assign.web.AuthController;
import edu.secure.assign.web.CustomerController;
import edu.secure.assign.web.GlobalExceptionHandler;
import edu.secure.assign.web.LoanController;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 코딩 적용 과제 자가 점검 테스트.
 * 통과한다고 해서 만점은 아니다. 이 테스트는 "최소 기준"이며,
 * 테스트로 확인하기 어려운 항목(IDOR, 동시성, CSRF 동작 등)은 보고서와 직접 호출 결과로 확인한다.
 * 테스트를 통과시키려고 테스트 코드를 수정하지 않는다. (src/test 는 제출 시 원본으로 교체되어 채점된다)
 */
class AssignmentCheckTest {

    private static final Path MAIN = Path.of("src/main");

    private static String read(String relative) {
        try {
            return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(relative + " 파일을 읽을 수 없습니다.", e);
        }
    }

    // ------------------------------------------------------------------ [1] SQL 삽입

    @Test
    @DisplayName("[1] MyBatis 매퍼에 ${} 가 남아 있는 곳은 ORDER BY ${sortBy} 하나뿐이어야 한다")
    void mapperHasNoStringSubstitution() {
        String xml = read("resources/mapper/LoanMapper.xml");
        String withoutAllowed = xml.replace("ORDER BY ${sortBy}", "");
        assertFalse(withoutAllowed.contains("${"), "WHERE 절 등에 ${} 치환이 남아 있습니다. #{} 로 바꾸세요.");
    }

    @Test
    @DisplayName("[1] 정렬 기준은 허용 목록에 있는 값만 통과해야 한다")
    void sortByWhitelist() {
        LoanService service = new LoanService(null);   // 허용 목록 검증이 DB 호출보다 먼저 일어나야 한다
        assertThrows(IllegalArgumentException.class, () -> service.findSorted("C001", "nosuch"));
        assertThrows(IllegalArgumentException.class, () -> service.findSorted("C001", "balance; DROP TABLE loan"));
        assertThrows(IllegalArgumentException.class, () -> service.findSorted("C001", "(SELECT 1 FROM dual)"));
    }

    // ------------------------------------------------------------------ [2] 접근 통제

    @Test
    @DisplayName("[2] 대출 단건 조회 / 상환 SQL 에 소유자(customer_id) 조건이 포함되어야 한다")
    void ownerConditionInQueries() {
        String xml = read("resources/mapper/LoanMapper.xml");
        assertTrue(Pattern.compile("(?s)<select[^>]*>[^<]*loan_id\\s*=\\s*#\\{loanId\\}[^<]*customer_id\\s*=\\s*#\\{\\w+\\}").matcher(xml).find(),
                "loan_id 와 customer_id 를 함께 조건으로 거는 조회 쿼리가 없습니다.");
        assertTrue(Pattern.compile("(?s)<update[^>]*>[^<]*customer_id\\s*=\\s*#\\{\\w+\\}").matcher(xml).find(),
                "상환(UPDATE) 쿼리에 소유자 조건이 없습니다.");
    }

    // ------------------------------------------------------------------ [3] 민감정보

    @Test
    @DisplayName("[3] 컨트롤러가 DB 원본 객체(Customer)를 그대로 반환하면 안 된다")
    void controllersDoNotReturnEntity() {
        for (Class<?> c : new Class<?>[]{CustomerController.class, LoanController.class, AuthController.class}) {
            for (Method m : c.getDeclaredMethods()) {
                assertNotEquals(Customer.class, m.getReturnType(),
                        c.getSimpleName() + "." + m.getName() + " 이(가) Customer 를 그대로 반환합니다. 응답 전용 DTO 를 만드세요.");
            }
        }
    }

    @Test
    @DisplayName("[3] Customer.toString() 에 주민번호/비밀번호 해시가 포함되면 안 된다")
    void customerToStringHasNoSecrets() {
        Customer c = new Customer("C100", "박민수", "950101-1234567", "010-1234-5678", "HASHVALUE");
        String s = c.toString();
        assertFalse(s.contains("950101-1234567"), "toString 에 주민번호가 포함되어 있습니다.");
        assertFalse(s.contains("HASHVALUE"), "toString 에 비밀번호 해시가 포함되어 있습니다.");
    }

    @Test
    @DisplayName("[3] 가입 요청 객체 전체를 로그로 남기면 안 된다")
    void signupDoesNotLogRequestObject() {
        String src = read("java/edu/secure/assign/web/AuthController.java");
        assertFalse(Pattern.compile("log\\.\\w+\\([^;]*\\breq\\s*\\)").matcher(src).find(),
                "log 에 req 객체 전체를 출력하고 있습니다. 식별자(customerId)만 남기세요.");
    }

    // ------------------------------------------------------------------ [4] 에러 처리

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("ORA-00942: table or view does not exist SELECT * FROM loan");
        }

        @GetMapping("/bad")
        String bad() {
            throw new IllegalArgumentException("secret-detail-message");
        }
    }

    @Test
    @DisplayName("[4] 예외 응답에 내부 정보(SQL, 예외 클래스명, 메시지)가 노출되면 안 된다")
    void errorResponseHasNoInternals() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        String body = mvc.perform(get("/boom")).andExpect(status().is5xxServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String leak : new String[]{"ORA-", "SELECT", "IllegalStateException", "java.lang", "table or view"}) {
            assertFalse(body.contains(leak), "응답에 내부 정보가 노출됨: " + leak + " / body=" + body);
        }

        String bad = mvc.perform(get("/bad")).andExpect(status().is4xxClientError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(bad.contains("secret-detail-message"), "입력 오류 응답에 예외 메시지가 그대로 노출됨: " + bad);
    }

    @Test
    @DisplayName("[4] 코드에서 e.toString() / printStackTrace() 를 응답이나 출력에 쓰지 않는다")
    void noToStringOrPrintStackTrace() throws IOException {
        try (var files = Files.walk(MAIN.resolve("java"))) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    assertFalse(src.contains("e.toString()"), p + " : e.toString() 사용");
                    assertFalse(src.contains("printStackTrace"), p + " : printStackTrace() 사용");
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
    }

    // ------------------------------------------------------------------ [5] 입력값 검증

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("[5] 정상 가입 요청은 검증을 통과해야 한다")
    void validSignupPasses() {
        var ok = new SignupRequest("C100", "박민수", "950101-1234567", "010-1234-5678", "Passw0rd!23");
        assertTrue(VALIDATOR.validate(ok).isEmpty());
    }

    @Test
    @DisplayName("[5] 잘못된 가입 요청은 검증에서 걸러져야 한다 (고객ID, 이름, 주민번호, 전화번호, 비밀번호)")
    void invalidSignupRejected() {
        assertFalse(VALIDATOR.validate(new SignupRequest("", "박민수", "950101-1234567", "010-1234-5678", "Passw0rd!23")).isEmpty(), "빈 고객ID");
        assertFalse(VALIDATOR.validate(new SignupRequest("C1' OR '1'='1", "박민수", "950101-1234567", "010-1234-5678", "Passw0rd!23")).isEmpty(), "특수문자가 섞인 고객ID");
        assertFalse(VALIDATOR.validate(new SignupRequest("C100", "<script>alert(1)</script>", "950101-1234567", "010-1234-5678", "Passw0rd!23")).isEmpty(), "스크립트가 포함된 이름");
        assertFalse(VALIDATOR.validate(new SignupRequest("C100", "박민수", "not-a-rrn", "010-1234-5678", "Passw0rd!23")).isEmpty(), "형식이 틀린 주민번호");
        assertFalse(VALIDATOR.validate(new SignupRequest("C100", "박민수", "950101-1234567", "123", "Passw0rd!23")).isEmpty(), "형식이 틀린 전화번호");
        assertFalse(VALIDATOR.validate(new SignupRequest("C100", "박민수", "950101-1234567", "010-1234-5678", "1234")).isEmpty(), "너무 짧은 비밀번호");
        assertFalse(VALIDATOR.validate(new SignupRequest(null, null, null, null, null)).isEmpty(), "null 값");
    }

    @Test
    @DisplayName("[5] 가입 API 의 요청 본문에 @Valid 가 적용되어야 한다")
    void signupUsesValid() throws NoSuchMethodException {
        Method m = AuthController.class.getMethod("signup", SignupRequest.class);
        boolean valid = Arrays.stream(m.getParameters()[0].getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Valid")
                        || a.annotationType().getSimpleName().equals("Validated"));
        assertTrue(valid, "signup 의 SignupRequest 파라미터에 @Valid 가 없습니다.");
    }

    // ------------------------------------------------------------------ [6] 해시 / 암호화

    @Test
    @DisplayName("[6] 비밀번호는 솔트가 포함된 느린 해시로 저장해야 한다")
    void passwordHashing() {
        String a = PasswordHasher.hash("Passw0rd!23");
        String b = PasswordHasher.hash("Passw0rd!23");
        assertNotEquals(a, b, "같은 비밀번호인데 결과가 항상 같습니다. 무작위 솔트가 필요합니다.");
        assertFalse(a.matches("[0-9a-fA-F]{32}"), "MD5 형식(32자리 hex)입니다.");
        assertTrue(PasswordHasher.verify("Passw0rd!23", a));
        assertTrue(PasswordHasher.verify("Passw0rd!23", b));
        assertFalse(PasswordHasher.verify("wrong", a));
    }

    private static String randomKey() {
        byte[] k = new byte[16];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    @DisplayName("[6] AES 암호화는 매번 다른 암호문을 만들고(IV), 복호화가 되어야 한다")
    void aesRoundTripAndRandomIv() {
        AesCrypto crypto = new AesCrypto(randomKey());
        String a = crypto.encrypt("950101-1234567");
        String b = crypto.encrypt("950101-1234567");
        assertNotEquals(a, b, "같은 평문이 항상 같은 암호문이 됩니다. (ECB 모드이거나 IV 가 고정)");
        assertEquals("950101-1234567", crypto.decrypt(a));
        assertEquals("950101-1234567", crypto.decrypt(b));
    }

    @Test
    @DisplayName("[6] 암호화 키는 주입된 값을 써야 하며, 코드에 하드코딩하면 안 된다")
    void aesKeyIsInjected() {
        String token = new AesCrypto(randomKey()).encrypt("900101-1234567");
        assertThrows(RuntimeException.class, () -> new AesCrypto(randomKey()).decrypt(token),
                "다른 키로도 복호화됩니다. 키가 코드에 고정되어 있지 않은지 확인하세요.");
        assertThrows(IllegalStateException.class, () -> new AesCrypto("").encrypt("x"),
                "키가 설정되지 않았는데도 암호화가 동작합니다.");
        assertFalse(read("java/edu/secure/assign/security/AesCrypto.java").contains("AES/ECB"), "ECB 모드를 사용하고 있습니다.");
    }

    @Test
    @DisplayName("[6] application.properties 에 DB 비밀번호와 키 값이 하드코딩되면 안 된다")
    void noHardcodedSecretsInProperties() {
        for (String line : read("resources/application.properties").split("\\R")) {
            String l = line.trim();
            if (l.startsWith("spring.datasource.password") || l.startsWith("assign.aes-key")) {
                String value = l.substring(l.indexOf('=') + 1).trim();
                assertTrue(value.matches("\\$\\{[A-Z0-9_]+}"),
                        "[" + l + "] 은(는) 기본값 없는 환경 변수 참조(${ENV_NAME}) 형태여야 합니다.");
            }
        }
    }

    // ------------------------------------------------------------------ [7] 금액 / 동시성

    @Test
    @DisplayName("[7] 월 이자는 BigDecimal 로 계산하고 원 단위 반올림(HALF_UP) 해야 한다")
    void interestRounding() {
        LoanService service = new LoanService(null);
        assertEquals(2917L, service.monthlyInterest(1_000_000L, "0.035"));   // 2916.666... → 2917
        assertEquals(229L, service.monthlyInterest(100_000L, "0.0275"));     // 229.1666... → 229
        assertEquals(0L, service.monthlyInterest(1L, "0.01"));
    }

    @Test
    @DisplayName("[7] 원금 0 이하, 금리 범위(0~1) 밖, 숫자가 아닌 금리는 거부해야 한다")
    void interestBoundaries() {
        LoanService service = new LoanService(null);
        assertThrows(IllegalArgumentException.class, () -> service.monthlyInterest(0, "0.035"));
        assertThrows(IllegalArgumentException.class, () -> service.monthlyInterest(-1_000_000, "0.035"));
        assertThrows(IllegalArgumentException.class, () -> service.monthlyInterest(1_000_000, "-1"));
        assertThrows(IllegalArgumentException.class, () -> service.monthlyInterest(1_000_000, "1.5"));
        assertThrows(IllegalArgumentException.class, () -> service.monthlyInterest(1_000_000, "abc"));
    }

    @Test
    @DisplayName("[7] 상환 금액이 0 이하이면 DB 조회 전에 거부해야 한다")
    void repayAmountValidation() {
        LoanService service = new LoanService(null);
        assertThrows(IllegalArgumentException.class, () -> service.repay(1001, "C001", -1_000_000));
        assertThrows(IllegalArgumentException.class, () -> service.repay(1001, "C001", 0));
    }

    @Test
    @DisplayName("[7] 상환은 조회-판단-갱신 분리 방식이 아니라 조건부 UPDATE 한 문장이어야 한다")
    void repayIsAtomic() {
        String service = read("java/edu/secure/assign/service/LoanService.java");
        assertFalse(service.contains("selectBalance") && service.contains("updateBalance"),
                "잔액을 읽은 뒤 자바에서 계산해 덮어쓰는 방식입니다. (경쟁 조건)");
        String xml = read("resources/mapper/LoanMapper.xml");
        assertTrue(Pattern.compile("(?s)<update[^>]*>[^<]*balance\\s*=\\s*balance\\s*-[^<]*balance\\s*>=").matcher(xml).find(),
                "balance = balance - ... WHERE ... AND balance >= ... 형태의 UPDATE 가 없습니다.");
    }

    // ------------------------------------------------------------------ [8] XSS / CSRF

    @Test
    @DisplayName("[8] search.jsp 는 출력 이스케이프를 적용해야 한다 (EL, 스크립틀릿, innerHTML)")
    void xssInSearchPage() {
        String jsp = read("webapp/WEB-INF/jsp/search.jsp");
        assertFalse(jsp.contains("<%="), "스크립틀릿 출력(<%= %>)이 남아 있습니다.");
        assertFalse(jsp.contains("innerHTML"), "innerHTML 이 남아 있습니다. textContent 를 쓰세요.");
        String unescaped = jsp.replaceAll("<c:out[^>]*>", "").replaceAll("fn:escapeXml\\([^)]*\\)", "");
        assertFalse(Pattern.compile("\\$\\{\\s*(q|param\\.q)\\s*}").matcher(unescaped).find(),"이스케이프 없는 ${q} 가 남아 있습니다. <c:out> 또는 fn:escapeXml 을 쓰세요.");
    }

    @Test
    @DisplayName("[8] 상환 폼은 CSRF 토큰을 발급하고 검증해야 한다")
    void csrfProtection() {
        String jsp = read("webapp/WEB-INF/jsp/repay-form.jsp");
        String page = read("java/edu/secure/assign/web/PageController.java");
        assertTrue(jsp.toLowerCase().contains("csrf"), "상환 폼에 CSRF 토큰 hidden 필드가 없습니다.");
        assertTrue(page.toLowerCase().contains("csrf") && page.contains("FORBIDDEN"),
                "상환 POST 처리에서 토큰을 검증하고 불일치 시 403 을 반환하는 코드가 없습니다.");
    }
}
