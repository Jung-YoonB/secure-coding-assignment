package edu.secure.assign.mapper;

import edu.secure.assign.domain.Loan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoanMapper {

    List<Loan> findByCustomer(@Param("customerId") String customerId);

    List<Loan> findSorted(@Param("customerId") String customerId, @Param("sortBy") String sortBy);

    Loan findById(@Param("loanId") long loanId);
    
    Loan findByIdAndCustomer(@Param("loanId") long loanId, @Param("customerId") String customerId);

    Long selectBalance(@Param("loanId") long loanId);

    int updateBalance(@Param("loanId") long loanId, @Param("balance") long balance);
    
    int repayConditional(@Param("loanId") long loanId, @Param("customerId") String customerId, 
    						@Param("amount") long amount);
}
