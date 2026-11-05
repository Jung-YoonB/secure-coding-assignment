package edu.secure.assign;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("edu.secure.assign.mapper")
public class AssignApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssignApplication.class, args);
    }
}
