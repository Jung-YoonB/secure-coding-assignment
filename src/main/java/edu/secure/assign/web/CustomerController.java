package edu.secure.assign.web;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.CustomerView;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.security.MaskingUtil;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerMapper customerMapper;

    public CustomerController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }
    
    /** DB 원본 대신 마스킹 된 응답 DTO 반환, 로그에는 식별자만 */
    @GetMapping("/customers/{customerId}")
    public CustomerView customer(@PathVariable String customerId, HttpSession session) {
        LoginController.requireCustomer(session);
        Customer c = customerMapper.findById(customerId);
        log.info("고객 조회: customerId={}", customerId);
        return new CustomerView(c.getCustomerId(), c.getCustomerName(),
                MaskingUtil.maskRrn(c.getRrn()), MaskingUtil.maskPhone(c.getPhone()));
    }
}
