package com.bookmall.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 图书目录服务入口。
 * <p>
 * 提供分类、图书查询，以及给订单服务调用的库存扣减和回补。扫描 {@code com.bookmall}
 * 以加载公共模块的全局异常处理。目录查询接口本身不要求登录。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
public class CatalogApplication {

    /** 启动目录服务。 */
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
