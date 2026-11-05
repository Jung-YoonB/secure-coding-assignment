package edu.secure.assign.service;

import java.util.Set;
import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LoanService {
	/** Order BY 허용 목록. 목록에 없는 값은 DB 호출 전에 거부 */
	private static final Set<String> ALLOWED_SORT = Set.of("loan_id", "balance");
	
    private final LoanMapper loanMapper;

    public LoanService(LoanMapper loanMapper) {
        this.loanMapper = loanMapper;
    }

    /** 정렬 기준(sortBy)으로 대출 목록 조회 */
    public List<Loan> findSorted(String customerId, String sortBy) {
    	if (!ALLOWED_SORT.contains(sortBy)) {
    		throw new IllegalArgumentException("허용되지 않는 정렬 기준");
    	}
        return loanMapper.findSorted(customerId, sortBy);
    }

    // 변경 전: 잔액 조회 -> 자바에서 계산 -> 덮어쓰기 (동시 요청 시 잔액 오류)
    // 변경 후: 조건부 UPDATE 한 문장으로 처리
    /** 대출 상환. 소유자, 잔액 확인과 차감을 UPDATE 한 문장으로 처리 */
    @Transactional
    public boolean repay(long loanId, String customerId, long amount) {
    	if (amount <= 0) {
    		throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
    	}    	
    	return loanMapper.repayConditional(loanId, customerId, amount) == 1;
    }

    // double 오차 제거: BigDecimal(문자열 생성자) + 원 단위 반올림(HALF_UP), 원금·금리 범위 검증
    /** 월 이자 = 원금 x 연이율 / 12 (원 단위 반올림 HALP_UP) */
    public long monthlyInterest(long principal, String annualRate) {
    	BigDecimal rate = new BigDecimal(annualRate);
    	if (principal <= 0) {
    		throw new IllegalArgumentException("원금은 0보다 커야 합니다.");
    	}
    	if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
    		throw new IllegalArgumentException("금리는 0 이상 1 이하여야 합니다.");
    	}
    	return BigDecimal.valueOf(principal)
    			.multiply(rate)
    			.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
    			.longValueExact();
	}
    
}
