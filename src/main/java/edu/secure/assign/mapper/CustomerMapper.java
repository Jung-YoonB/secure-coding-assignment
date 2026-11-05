package edu.secure.assign.mapper;

import edu.secure.assign.domain.Customer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMapper {

    @Select("SELECT customer_id, customer_name, rrn, phone, password_hash FROM customer WHERE customer_id = #{customerId}")
    Customer findById(@Param("customerId") String customerId);

    @Insert("INSERT INTO customer (customer_id, customer_name, rrn, phone, password_hash) "
            + "VALUES (#{customerId}, #{customerName}, #{rrn}, #{phone}, #{passwordHash})")
    int insert(Customer customer);
}
