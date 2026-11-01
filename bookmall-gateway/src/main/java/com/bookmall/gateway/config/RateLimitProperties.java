package com.bookmall.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关令牌桶速率。登录与注册各一个桶，单位是每秒许可数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.rate-limit")
public class RateLimitProperties {

    /** {@code POST /api/users/login} 每秒许可。 */
    private double loginRate = 5.0;

    /** {@code POST /api/users/register} 每秒许可。 */
    private double registerRate = 5.0;
}
