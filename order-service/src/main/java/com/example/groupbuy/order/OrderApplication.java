package com.example.groupbuy.order;

import com.example.groupbuy.common.ApiErrorAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Import(ApiErrorAdvice.class)
public class OrderApplication {
    /** 启动订单服务及定时补偿任务。 */
    public static void main(String[] args) { SpringApplication.run(OrderApplication.class, args); }
}
