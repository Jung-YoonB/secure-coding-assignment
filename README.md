# secure-coding-assignment (시큐어 코딩 적용 과제)

금융권 보안 코딩 과정 **적용 과제용** 프로젝트 (Spring Boot 3.3 + MyBatis + JSP + Oracle 21c XE, Java 17).
수업 실습(`secure-coding-lab`)에서 `/vuln` 과 `/safe` 를 나눠 비교했다면, 이 프로젝트는 **취약 코드만 들어 있다.**
**소스 전체를 직접 점검해 취약점 8개 영역을 찾아 수정**하고, 수정 내역을 `REPORT.md` 에 정리해 제출한다.

> ⚠ 이 프로젝트는 **일부러 취약하게 만든 코드**다. 로컬 환경에서만 실행하고, 외부에 노출하거나 실제 서비스에 복사하지 않는다. 모든 데이터는 가상 데이터다.

- 제출 기한: **2026-11-06(금) 18:00**
- 과제 안내 문서: `raw/edu/secure/financial/test/02_시큐어_코딩_적용_과제_안내.md`

---

## 1. 환경 준비

1. **DB**: 수업 실습과 같은 Oracle 21c XE / `C##SECURE` 계정을 쓴다. 이미 `secure-coding-lab` 을 돌려 봤다면 `sql/01_schema.sql` 을 다시 실행해 데이터를 초기화한다. (`customer`, `loan` 테이블을 DROP 후 재생성)
2. **환경 변수** (아래 값은 PowerShell 예시이며, 값은 본인 환경에 맞게 쓴다)

   ```powershell
   $env:ASSIGN_DB_PASSWORD = "SECURE"

   # AES-128 키(Base64) 생성
   $b = New-Object byte[] 16
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
   $env:ASSIGN_AES_KEY = [Convert]::ToBase64String($b)
   ```

   > 처음 받은 `application.properties` 는 DB 비밀번호가 하드코딩되어 있어 환경 변수 없이도 뜬다. **이것도 수정 대상**이다. 수정 후에는 위 환경 변수가 없으면 기동되지 않는다.
3. **실행** (Java 17)

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   브라우저에서 `http://localhost:8080/login-page` → `C001` 로그인.

## 2. 프로젝트 구조

| 경로 | 설명 |
|---|---|
| `web/LoanController` | 대출 조회, 정렬, 상환, 이자 계산 API (`/api/loans/**`, `/api/interest`) |
| `web/CustomerController` | 고객 조회 API (`/api/customers/{id}`) |
| `web/AuthController` | 회원가입 API (`POST /api/signup`) |
| `web/LoginController` | 간이 로그인 (**수정 대상 아님**) |
| `web/GlobalExceptionHandler` | 공통 예외 처리 |
| `web/PageController` + `webapp/WEB-INF/jsp/*` | 검색 화면(`/search`), 상환 폼(`/repay-form`) |
| `service/LoanService` | 정렬, 상환, 이자 계산 |
| `mapper/*`, `resources/mapper/LoanMapper.xml` | MyBatis 매퍼 |
| `security/` | 마스킹, 비밀번호 해시, AES 암호화 |
| `attack/csrf-attack.html` | CSRF 확인용 공격 페이지 (수정 대상 아님) |
| `src/test/.../AssignmentCheckTest` | 자가 점검 테스트 (**수정 금지**) |
| `REPORT.md` | 수정 내역 보고서 양식 (**작성하여 제출**) |

## 3. 자가 점검

DB 없이 실행되는 최소 기준 테스트다. 처음에는 대부분 실패하는 것이 정상이다.

```powershell
.\mvnw.cmd test
```

- 초기 상태: 21개 중 20개 실패
- 테스트를 통과시키려고 `src/test` 를 고치지 않는다. (제출본의 테스트는 원본으로 교체되어 채점된다)
- 테스트 통과는 **최소 기준**이다. IDOR, 동시성, CSRF 동작 등은 직접 호출해 확인하고 `REPORT.md` 에 근거를 남긴다.

## 4. 정적 분석 (선택, 가산)

```powershell
.\mvnw.cmd -Psast compile spotbugs:spotbugs
```

리포트는 `target/spotbugsXml.xml` 에 생성된다. 수정 전/후 결과를 `REPORT.md` 에 비교해 적으면 가산점.

## 5. 제출 전 확인

- [ ] `.\mvnw.cmd test` 21개 모두 통과
- [ ] 애플리케이션이 정상 기동되고 로그인, 대출 조회, 상환이 동작
- [ ] `REPORT.md` 작성 완료
- [ ] `target/`, `.idea/`, `.vscode/` 제외하고 압축
- [ ] 소스, 설정 파일, 로그, 보고서에 **실제 비밀번호/키 값**이 들어 있지 않음
