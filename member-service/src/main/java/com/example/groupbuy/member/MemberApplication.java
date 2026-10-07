package com.example.groupbuy.member;

import com.example.groupbuy.common.ApiErrorAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ApiErrorAdvice.class)
public class MemberApplication {
    /** 启动用户服务。 */
    public static void main(String[] args) {
        SpringApplication.run(MemberApplication.class, args);
    }
}
