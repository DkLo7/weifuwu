package com.example.groupbuy.catalog;

import com.example.groupbuy.common.ApiErrorAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ApiErrorAdvice.class)
public class CatalogApplication {
    /** 启动商品库存服务。 */
    public static void main(String[] args) { SpringApplication.run(CatalogApplication.class, args); }
}
