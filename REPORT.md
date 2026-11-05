# 시큐어 코딩 적용 보고서

- 이름: 배정윤
- 제출일: 2026-11-06
- 자가 점검 테스트 결과: `21 / 21` 통과 (수정 전 `1 / 21`)

## 항목별 수정 내역

항목마다 **취약 원인 → 공격 재현(수정 전) → 수정 내용 → 확인(수정 후)** 순서로 적는다.
수정하지 못한 항목은 비워 두지 말고 이유와 시도한 내용을 적는다.

### [1] SQL 삽입

- 취약 원인:
    - `LoanMapper.xml` 의 `findByCustomer` 가 `'${customerId}'` 문자열 치환을 사용
        - 입력값이 SQL 문장에 그대로 끼워져 SQL 문법으로 해석됨
    - `findSorted` 의 `ORDER BY ${sortBy}` 에 검증 없이 외부 입력 전달
- 공격 재현(수정 전): (호출한 요청과 결과)
    - `GET /api/loans?customerId=' OR '1'='1`
        - WHERE 조건이 항상 참이 되어 C001 외 C002, C003 대출까지 전체(1001~1004) 반환
    - `GET /api/loans/sorted?customerId=C001&sortBy=nosuch`
        - 500, 응답에 DB 오류 내용(ORA 코드, SQL) 노출
- 수정 내용: (파일, 변경 전/후 핵심 코드)
    - `LoanMapper.xml` findByCustomer
        - 변경 전: `WHERE customer_id = '${customerId}'`
        - 변경 후: `WHERE customer_id = #{customerId}` (PreparedStatement 바인딩 -> 입력값은 값으로만 전달)
    - `LoanService.findSorted()`
        - 컬럼명은 바인딩할 수 없으므로 `${sortBy}` 는 유지하고, DB 호출 전에 허용 목록 검증 추가
    ```java
    private static final Set<String> ALLOWED_SORT = Set.of("loan_id", "balance");

    if (!ALLOWED_SORT.contains(sortBy)) {
        throw new IllegalArgumentException("허용되지 않는 정렬 기준");
    }
    ```
- 확인(수정 후):
    - `GET /api/loans?customerId=' OR '1'='1`
        - `[]` (해당 문자열과 같은 ID가 없으므로 빈 결과)
    - `GET /api/loans/sorted?customerId=C001&sortBy=balance`
        - 정상 조회
    - `GET /api/loans/sorted?customerId=C001&sortBy=nosuch`
        - 400 `{"message":"잘못된 요청입니다."}`

### [2] 접근 통제 (IDOR)

- 취약 원인:
    - 단건 조회 `findById` 가 `loan_id` 만 조건으로 사용
    - 컨트롤러가 `requireCustomer(session)` 로 로그인 여부만 확인
        - 반환된 로그인 고객 ID를 조회 조건에 쓰지 않고 버림
    - 상환 UPDATE 도 `loan_id` 만 조건으로 사용
- 공격 재현(수정 전):
    - C001 로그인 후 `GET /api/loans/1002`
        - `{"loanId":1002,"customerId":"C002","balance":30000000,"status":"ACTIVE"}` (타인 대출 조회)
- 수정 내용:
    - `LoanMapper.xml`
        - 소유자 조건을 포함한 조회 `findByIdAndCustomer` 추가
    ```xml
    <select id="findByIdAndCustomer" resultType="edu.secure.assign.domain.Loan">
        SELECT loan_id, customer_id, balance, status
          FROM loan
         WHERE loan_id = #{loanId}
           AND customer_id = #{customerId}
    </select>
    ```
    - `LoanController.loan()`
        - 세션의 고객 ID(`me`)를 조건으로 전달하고, 결과가 없으면 404
    ```java
    String me = LoginController.requireCustomer(session);
    Loan loan = loanMapper.findByIdAndCustomer(loanId, me);
    if (loan == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    ```
    - 상환
        - [7]의 조건부 UPDATE 에 `AND customer_id = #{customerId}` 를 포함해 함께 처리
- 확인(수정 후):
    - `GET /api/loans/1001` (본인)
        - 정상 조회
    - `GET /api/loans/1002` (타인)
        - 404 `{"message":"요청을 처리할 수 없습니다."}`
    - `GET /api/loans/9999` (없는 번호)
        - 404, 위와 같은 응답
- 타인 자원 접근 시 응답 코드를 무엇으로 정했고, 그 이유는?
    - **404**
    - 403으로 응답하면 "그 번호의 대출이 존재하지만 권한이 없다"는 사실이 드러남
        - 공격자가 번호를 바꿔 가며 다른 고객 대출의 존재 여부를 알아낼 수 있음
    - 없는 자원과 같은 404를 주면 둘을 구분할 수 없음

### [3] 민감정보 노출

- 취약 원인: (응답 / 로그 각각)
    - 응답
        - `CustomerController` 가 DB 원본 객체 `Customer` 를 그대로 반환
        - 주민번호, 전화번호 원문과 `passwordHash` 항목이 JSON 에 포함
    - 로그
        - `log.info("고객 조회: {}", c)` -> `Customer.toString()` 이 주민번호, 전화번호, 비밀번호 해시를 모두 출력
        - `AuthController` 의 `log.info("가입 요청: {}", req)` -> 가입 요청 객체 전체(주민번호, **비밀번호 평문**)가 서버 로그에 기록
- 공격 재현(수정 전):
    - `GET /api/customers/C001`
        - `{"customerId":"C001","customerName":"홍길동","rrn":"900101-1234567","phone":"010-1111-2222","passwordHash":null}`
        - 초기 데이터는 해시가 NULL이지만, 항목 자체가 응답에 포함됨
- 수정 내용:
    - `domain/CustomerView` (신규, 응답 전용 DTO)
        - `customerId`, `customerName`, `rrnMasked`, `phoneMasked` 만 포함
        - 비밀번호 해시는 필드 자체가 없음
    - `CustomerController.customer()`
        - `MaskingUtil.maskRrn()`, `MaskingUtil.maskPhone()` 으로 마스킹해서 `CustomerView` 반환
    - `Customer.toString()`
        - 변경 후: `"Customer{customerId='" + customerId + "'}"` (식별자만 출력)
    - 로그
        - `log.info("고객 조회: customerId={}", customerId)`
        - `log.info("가입 요청: customerId={}", req.customerId())`
- 확인(수정 후):
    - `GET /api/customers/C001`
        - `{"customerId":"C001","customerName":"홍길동","rrnMasked":"900101-1******","phoneMasked":"010-****-2222"}`
    - 서버 로그
        - `고객 조회: customerId=C001` 만 기록됨

### [4] 에러 처리

- 취약 원인:
    - `GlobalExceptionHandler` 가 모든 예외를 500으로 응답
    - 응답 본문에 예외 객체 문자열을 그대로 내려줌
        - 예외 클래스명, 예외 메시지, SQL, DB 오류 코드 노출
    - 서버 로그에는 아무것도 기록하지 않아 원인 추적도 불가
- 공격 재현(수정 전):
    - `GET /api/interest?principal=1000000&rate=abc`
        - 500, `java.lang.NumberFormatException: For input string: "abc"` 노출
    - `POST /api/signup` (필수 값 누락)
        - 500, `org.mybatis.spring.MyBatisSystemException` 노출
    - 로그인 없이 `POST /repay-form`
        - `org.springframework.web.server.ResponseStatusException: 401 UNAUTHORIZED` 노출
- 수정 내용: (예외 종류별로 어떤 상태 코드/메시지를 내려주는지)

    | 예외 | 상태 코드 | 응답 본문 |
    |---|---|---|
    | `ResponseStatusException` | 원래 코드 유지 (401, 403, 404) | `{"message":"요청을 처리할 수 없습니다."}` |
    | `IllegalArgumentException` | 400 | `{"message":"잘못된 요청입니다."}` |
    | `MethodArgumentNotValidException` | 400 | `{"message":"입력값을 확인해 주세요."}` |
    | 그 외 `Exception` | 500 | `{"message":"요청을 처리할 수 없습니다.","errorId":"(UUID)"}` |

    - `IllegalArgumentException`
        - 음수 금액, 범위 밖 금리, 숫자가 아닌 금리, 허용되지 않은 정렬 기준
    - `MethodArgumentNotValidException`
        - `@Valid` 검증 실패 ([5])
    - 500 일 때
        - `log.error("처리 중 오류 errorId={}", errorId, e)` 로 errorId 와 스택 트레이스를 **서버 로그에만** 기록
        - 사용자가 errorId 를 알려 주면 로그에서 원인을 찾을 수 있음
- 확인(수정 후):
    - `GET /api/interest?principal=1000000&rate=abc`
        - 400 `{"message":"잘못된 요청입니다."}`
    - 응답 어디에도 예외 클래스명, SQL, ORA 코드가 나오지 않음

### [5] 입력값 검증

- 취약 원인:
    - `POST /api/signup` 이 요청 본문을 검증 없이 그대로 DB 에 저장
- 공격 재현(수정 전):
    - `{"customerId":"HACK1","customerName":"<script>alert(1)</script>","rrn":"abc","phone":"!!!","password":"1"}`
        - `{"customerId":"HACK1"}` 가입 성공
        - 스크립트 이름, 형식이 틀린 주민번호·전화번호, 1글자 비밀번호가 그대로 저장
- 수정 내용: (필드별 검증 규칙과 그렇게 정한 이유)
    - `SignupRequest` 각 필드에 검증 어노테이션 적용
    - `AuthController.signup()` 의 파라미터에 `@Valid` 추가
    - 공통 방침: 허용할 형식만 정의하는 **허용 목록 방식**
        - 금지 문자를 나열하는 방식은 빠뜨리는 문자가 생길 수 있음

    | 항목 | 규칙 | 이유 |
    |---|---|---|
    | 고객 ID | `@NotBlank`, `^[A-Za-z0-9]{4,10}$` | DB 컬럼이 VARCHAR2(10). 따옴표, 공백, 기호를 허용하지 않아 SQL 삽입·스크립트에 쓰이는 문자를 원천 차단 |
    | 이름 | `@NotBlank`, `^[가-힣A-Za-z ]{2,16}$` | `<`, `>` 등 기호를 막아 저장형 XSS 차단. DB 컬럼이 VARCHAR2(50) 바이트이고 한글은 UTF-8 에서 3바이트라 16자로 제한 |
    | 주민번호 | `@NotBlank`, `^\d{6}-\d{7}$` | 정해진 형식 외 입력 차단. `MaskingUtil.maskRrn()` 이 7번째 글자의 `-` 를 기준으로 마스킹하므로 형식이 보장돼야 함 |
    | 전화번호 | `@NotBlank`, `^01[016789]-\d{3,4}-\d{4}$` | 휴대전화 번호 형식만 허용. `MaskingUtil.maskPhone()` 이 `-` 로 3부분을 나눠 마스킹하므로 형식이 보장돼야 함 |
    | 비밀번호 | `@NotBlank`, `@Size(min=8, max=64)`, 영문·숫자·특수문자 각 1개 이상 | 짧고 단순한 비밀번호는 무차별 대입에 약함. 최대 길이는 느린 해시(PBKDF2)에 아주 긴 값을 넣어 서버 부하를 일으키는 것을 막기 위함 |

    - 검증 실패 시
        - 400 `{"message":"입력값을 확인해 주세요."}`
        - 어떤 규칙에 걸렸는지는 응답하지 않음
- 확인(수정 후): (정상 요청 / 비정상 요청 각각)
    - 정상 요청 `{"customerId":"C100","customerName":"박민수","rrn":"950101-1234567","phone":"010-1234-5678","password":"Passw0rd!23"}`
        - 가입 성공
    - 비정상 요청 (수정 전 재현 값)
        - 400 `{"message":"입력값을 확인해 주세요."}`

### [6] 비밀번호 해시 / 암호화 / 키 관리

- 취약 원인: (해시, 암호화, DB 접속 정보, 키 각각)
    - 해시
        - 솔트 없는 MD5 -> 계산이 너무 빨라 무차별 대입에 약함
        - 같은 비밀번호는 항상 같은 해시가 되어 레인보우 테이블로 역추적 가능
    - 암호화
        - ECB 모드 -> 같은 평문이 항상 같은 암호문이 되어 패턴이 드러남
    - DB 접속 정보
        - `application.properties` 에 `spring.datasource.password=(하드코딩된 값)`
    - 키
        - `AesCrypto` 코드에 고정 키 상수 -> 소스가 유출되면 키도 유출
- 수정 내용:

    | 대상 | 변경 전 | 변경 후 |
    |---|---|---|
    | 비밀번호 | 솔트 없는 MD5 | 무작위 솔트 + PBKDF2WithHmacSHA256 |
    | 주민번호 암호화 | ECB 모드 + 코드에 고정 키 | `AES/GCM/NoPadding` + 매번 새로운 IV + 주입받은 키 |
    | 설정 파일 | DB 비밀번호, AES 키 하드코딩 | 환경 변수 참조 (기본값 없음) |

    - `PasswordHasher`
        - 16바이트 `SecureRandom` 솔트, 60만 회 반복, 256비트
        - 저장 형식 `salt:hash`
        - 검증은 `MessageDigest.isEqual` (상수 시간 비교)
    - `AesCrypto`
        - 매번 새로운 12바이트 IV 생성, IV + 암호문을 이어 붙여 저장
        - 코드의 고정 키 상수 삭제
        - 키가 비어 있으면 `IllegalStateException`
        - 생성자 `@Value("${assign.aes-key:}")` 의 기본값(`:` 뒤 빈 값)도 제거
    - `application.properties`
        - `spring.datasource.password=${ASSIGN_DB_PASSWORD}`
        - `assign.aes-key=${ASSIGN_AES_KEY}`
    - 기본값을 두지 않은 이유
        - 기본값이 있으면 설정을 빠뜨려도 약한 값으로 조용히 동작함
        - 없으면 기동 단계에서 바로 실패해 누락을 알 수 있음
- 확인(수정 후):
    - 같은 비밀번호를 두 번 해시
        - 결과가 매번 다르고, `verify()` 는 둘 다 통과
    - 같은 주민번호를 두 번 암호화
        - 암호문이 매번 다름, 다른 키로는 복호화 실패
    - 환경 변수 없이 실행
        - `Could not resolve placeholder 'ASSIGN_AES_KEY'` 로 기동 실패
    - 환경 변수 설정 후 실행
        - 정상 기동, 회원가입 시 비밀번호는 `salt:hash` 형식으로 저장

### [7] 금액 계산 / 동시성

- 취약 원인: (이자 계산, 상환 금액 검증, 경쟁 조건 각각)
    - 이자 계산
        - `double` 로 계산 후 `(long)` 변환 -> 부동소수점 오차, 소수점이 반올림이 아닌 버림 처리
        - 원금 0 이하, 금리 0~1 범위 밖 값을 거부하지 않음
    - 상환 금액 검증
        - 0 이하 금액을 거부하지 않음
        - `balance < amount` 비교가 음수에서는 항상 거짓이 되어 통과하고, `balance - (-금액)` 으로 잔액이 증가
    - 경쟁 조건
        - 잔액 조회 -> 자바에서 판단·계산 -> 덮어쓰기로 나뉘어 있음
        - 동시 요청 시 두 요청이 같은 잔액을 읽고 각자 덮어써 한쪽 차감이 사라짐 (Lost Update)
- 공격 재현(수정 전): (음수 상환 결과, 동시 요청 결과 등)
    - `GET /api/interest?principal=1000000&rate=0.035`
        - `2916` (정답 2916.67 의 반올림 값은 2917)
    - `GET /api/interest?principal=1000000&rate=-1`
        - 음수 이자 `-83333` 계산
    - `POST /api/loans/1001/repay?amount=-1000000`
        - `{"success":true}`
        - 1001 잔액이 12,500,000 -> 13,500,000 으로 **증가**
    - 동시 요청
        - 코드 구조(조회와 갱신 분리)로 판단, 직접 동시 호출 재현은 하지 않음
- 수정 내용:
    - `LoanService.monthlyInterest()`
        - `new BigDecimal(annualRate)` (문자열 생성자로 오차 없이 생성)
        - `.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)` 원 단위 반올림
        - 원금 0 이하, 금리 0 미만 또는 1 초과 -> `IllegalArgumentException`
        - 숫자가 아닌 금리 -> `BigDecimal` 생성 시 `NumberFormatException` (`IllegalArgumentException` 의 하위 클래스)
    - `LoanService.repay()`
        - 0 이하 금액은 DB 호출 전에 `IllegalArgumentException`
        - 소유자·잔액 확인과 차감을 조건부 UPDATE 한 문장으로 처리
    ```xml
    <update id="repayConditional">
        UPDATE loan
           SET balance = balance - #{amount}
         WHERE loan_id = #{loanId}
           AND customer_id = #{customerId}
           AND balance >= #{amount}
    </update>
    ```
    - 조건부 UPDATE 동작
        - 변경된 행 수가 1이면 성공, 0이면 실패 (타인 대출, 없는 대출, 잔액 부족)
        - DB 가 UPDATE 하는 동안 해당 행을 잠그므로, 동시 요청이 와도 조건 확인과 차감이 한 번에 처리됨
- 확인(수정 후):
    - `GET /api/interest?principal=1000000&rate=0.035`
        - `2917`
    - `rate=-1`, `rate=1.5`, `rate=abc`, `principal=0`
        - 400 `{"message":"잘못된 요청입니다."}`
    - `POST /api/loans/1001/repay?amount=-1000000`
        - 400, 잔액 변화 없음
    - `POST /api/loans/1002/repay?amount=1000` (타인 대출)
        - `{"success":false}`

### [8] XSS / CSRF

- 취약 원인:
    - XSS
        - `search.jsp` 가 검색어를 이스케이프 없이 3곳에서 출력
        - EL `${q}`, 스크립틀릿 `request.getParameter("q")` 직접 출력, JS `innerHTML`
    - CSRF
        - 상환 POST 가 세션 쿠키만으로 요청자를 확인
        - 사용자가 로그인한 상태라면 다른 사이트에서 보낸 요청도 처리됨
- 공격 재현(수정 전):
    - `GET /search?q=<script>alert(1)</script>`
        - 알림창 실행
    - 로그인 상태에서 `attack/csrf-attack.html` 실행
        - `401 UNAUTHORIZED`
        - 크롬의 SameSite 기본값(Lax) 때문에 다른 사이트에서 보낸 POST 에 세션 쿠키가 붙지 않음
        - **브라우저 설정에 의존한 차단**일 뿐 서버에는 CSRF 방어가 없었음. 브라우저 종류·버전·설정에 따라 쿠키가 전송되면 상환이 처리됨
- 수정 내용:
    - XSS (`search.jsp`)

    | 위치 | 변경 전 | 변경 후 |
    |---|---|---|
    | EL | `${q}` | `<c:out value="${q}" />` |
    | 스크립틀릿 | `request.getParameter("q")` 직접 출력 | `${fn:escapeXml(param.q)}` |
    | JS | `innerHTML` | `textContent` |

    - CSRF (`PageController`, `repay-form.jsp`)
        - `GET /repay-form`: `SecureRandom` 32바이트 토큰 발급 -> 세션과 폼 hidden 필드(`_csrf`)에 함께 저장
        - `POST /repay-form`: 세션 토큰과 요청 토큰을 `MessageDigest.isEqual` 로 비교, 없거나 다르면 403 (`HttpStatus.FORBIDDEN`)
        - 공격 페이지는 다른 출처라서 사용자 세션의 토큰 값을 읽을 수 없으므로 올바른 토큰을 넣을 수 없음
- 확인(수정 후): (`attack/csrf-attack.html` 실행 결과 포함)
    - `GET /search?q=<script>alert(1)</script>`
        - 스크립트가 실행되지 않고 `<script>alert(1)</script>` 가 문자열로 표시
    - `/repay-form` 화면에서 상환
        - `상환 처리: true` (정상 기능 유지)
    - 로그인 상태에서 토큰 없이 `POST /repay-form`
        - `403`
    - `attack/csrf-attack.html` 실행
        - `401`, 상환되지 않음 (1001 잔액 변화 없음)

## 수업 내용과 다르게 적용한 부분 / 어려웠던 점

- **다르게 적용한 부분**
    - 암호화 클래스 이름
        - 실습의 `AesGcmCrypto` 가 아닌 기존 `AesCrypto` 를 유지
        - 다른 코드와 테스트가 이 이름을 참조하기 때문
    - `@Value` 기본값
        - `:` 를 제거해 코드에서도 기본값 없이 주입받도록 함
    - `@Valid` 검증 실패 처리
        - `MethodArgumentNotValidException` 은 `IllegalArgumentException` 의 하위 클래스가 아님
        - 따로 처리하지 않으면 500으로 응답되므로 400 처리를 추가
    - 회원가입 고객의 주민번호 마스킹
        - 저장된 값이 암호문이라 `maskRrn()` 형식 검사를 통과하지 못해 `******-*******` 로 전부 가려짐
        - 정보가 노출되지 않는 방향이므로 그대로 둠
- **어려웠던 점**
    - 자가 점검 테스트가 소스를 **글자로 검사**함
        - 변경 전 코드를 주석으로 남기면 테스트가 실패
        - 변경 전 코드는 보고서로 옮기고 소스에서는 삭제
    - MyBatis XML 컬럼명 오타(`balnace`)
        - XML 의 SQL 은 컴파일 단계에서 검사되지 않아 실행 전까지 알기 어려움
        - 자가 점검 테스트로 발견
    - 처음 보고 알 수 없는 코드가 많음
        - 실습 문제를 번갈아보며 이게 맞는 건지 감이 안 잡혀 대조가 불명확한 부분이 있었음
        - 관련하여 자바 math 패키지의 BigDecimal 클래스 등 기초 다지기 필요성을 느낌
    - 보안 관련 용어 정리 필요성을 느낌
        - 어떤 기능인지는 알겠으나, 용어가 어려워 헷갈리는 경우가 있었음