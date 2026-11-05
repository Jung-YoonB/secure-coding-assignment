package edu.secure.assign.web;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoanController {

    private final LoanMapper loanMapper;
    private final LoanService loanService;

    public LoanController(LoanMapper loanMapper, LoanService loanService) {
        this.loanMapper = loanMapper;
        this.loanService = loanService;
    }

    @GetMapping("/loans")
    public List<Loan> loans(@RequestParam String customerId, HttpSession session) {
        LoginController.requireCustomer(session);
        return loanMapper.findByCustomer(customerId);
    }

    @GetMapping("/loans/sorted")
    public List<Loan> sorted(@RequestParam String customerId, @RequestParam String sortBy, HttpSession session) {
        LoginController.requireCustomer(session);
        return loanService.findSorted(customerId, sortBy);
    }
    
    /** 로그인한 본인의 대출만 조회. 타인/없는 것 모두 404로 같은 응답 */
    @GetMapping("/loans/{loanId}")
    public Loan loan(@PathVariable long loanId, HttpSession session) {
    	String me = LoginController.requireCustomer(session);
    	Loan loan = loanMapper.findByIdAndCustomer(loanId, me);
    	if (loan == null) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    	}
    	return loan;
    }

    @PostMapping("/loans/{loanId}/repay")
    public Map<String, Object> repay(@PathVariable long loanId, @RequestParam long amount, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        return Map.of("success", loanService.repay(loanId, me, amount));
    }

    @GetMapping("/interest")
    public Map<String, Object> interest(@RequestParam long principal, @RequestParam String rate) {
        return Map.of("monthlyInterest", loanService.monthlyInterest(principal, rate));
    }
}
