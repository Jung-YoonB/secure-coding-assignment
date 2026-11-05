package edu.secure.assign.web;

import edu.secure.assign.mapper.CustomerMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** 간이 로그인(고객 ID만으로 세션 생성). 과제 범위에서는 로그인 방식 자체는 수정하지 않는다. */
@RestController
public class LoginController {

    static final String SESSION_KEY = "customerId";

    private final CustomerMapper customerMapper;

    public LoginController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String customerId, HttpSession session) {
        if (customerMapper.findById(customerId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        session.setAttribute(SESSION_KEY, customerId);
        return Map.of("loggedInAs", customerId);
    }

    /** 세션의 고객 ID. 로그인하지 않았으면 401 */
    static String requireCustomer(HttpSession session) {
        Object id = session.getAttribute(SESSION_KEY);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return id.toString();
    }
}
