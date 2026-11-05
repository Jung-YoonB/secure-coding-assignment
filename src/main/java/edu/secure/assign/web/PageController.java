package edu.secure.assign.web;

import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면(JSP) 컨트롤러 */
@Controller
public class PageController {
	private static final String CSRF_KEY = "csrfToken";
	private final SecureRandom random = new SecureRandom();
	
    private final LoanService loanService;

    public PageController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/login-page")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        return "search";
    }
    
    /** 폼을 열 때마다 예측 불가능한 CSRF 토큰을 발급해 세션과 폼에 함께 넣음 */
    @GetMapping("/repay-form")
    public String repayForm(Model model, HttpSession session) {
    	byte[] bytes = new byte[32];
    	random.nextBytes(bytes);
    	String token = HexFormat.of().formatHex(bytes);
    	session.setAttribute(CSRF_KEY, token);
    	model.addAttribute("csrf", token);
        return "repay-form";
    }
    
    /** 세션 토큰과 폼 토큰이 다르거나 없으면 403 */
    @PostMapping("/repay-form")
    @ResponseBody
    public String repay(@RequestParam long loanId, @RequestParam long amount,
    					@RequestParam(name = "_csrf", required = false) String csrf, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        Object expected = session.getAttribute(CSRF_KEY);
        if (expected == null || csrf == null || !MessageDigest.isEqual(
        		expected.toString().getBytes(StandardCharsets.UTF_8), csrf.getBytes(StandardCharsets.UTF_8))) {
        	throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return "상환 처리: " + loanService.repay(loanId, me, amount);
    }
}
