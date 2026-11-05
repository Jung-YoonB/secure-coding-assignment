-- =============================================================================
-- 보안 코딩 과제용 스키마 (Oracle 21c XE 기준)
-- =============================================================================
-- [사전 준비: 과제 계정(C##SECURE) 생성]
-- Oracle 21c XE 기본 접속 서비스명은 'XE'(CDB$ROOT)이며, CDB에서 계정을 만들 때는
-- 계정명 앞에 'C##' 접두사를 붙여야 합니다.
-- 관리자(SYSTEM 또는 SYS)로 접속하여 아래 2줄을 먼저 실행해 계정을 만든 뒤,
-- 생성된 'C##SECURE' 계정으로 접속하여 이 스크립트(F5)를 실행하세요.
--
--   CREATE USER C##SECURE IDENTIFIED BY SECURE DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
--   GRANT CONNECT, RESOURCE TO C##SECURE;
-- =============================================================================

BEGIN EXECUTE IMMEDIATE 'DROP TABLE loan PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE customer PURGE'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

CREATE TABLE customer (
    customer_id    VARCHAR2(10)  PRIMARY KEY,
    customer_name  VARCHAR2(50)  NOT NULL,
    rrn            VARCHAR2(200) NOT NULL,   -- 가상 주민등록번호
    phone          VARCHAR2(20),
    password_hash  VARCHAR2(300)
);

CREATE TABLE loan (
    loan_id      NUMBER(10)    PRIMARY KEY,
    customer_id  VARCHAR2(10)  NOT NULL REFERENCES customer(customer_id),
    balance      NUMBER(15)    NOT NULL,
    status       VARCHAR2(10)  DEFAULT 'ACTIVE' NOT NULL
);

INSERT INTO customer VALUES ('C001', '홍길동', '900101-1234567', '010-1111-2222', NULL);
INSERT INTO customer VALUES ('C002', '김영희', '850505-2345678', '010-3333-4444', NULL);
INSERT INTO customer VALUES ('C003', '이철수', '780303-1456789', '010-5555-6666', NULL);

INSERT INTO loan VALUES (1001, 'C001', 12500000, 'ACTIVE');
INSERT INTO loan VALUES (1002, 'C002', 30000000, 'ACTIVE');
INSERT INTO loan VALUES (1003, 'C003', 5000000,  'ACTIVE');
INSERT INTO loan VALUES (1004, 'C001', 8000000,  'ACTIVE');

COMMIT;
