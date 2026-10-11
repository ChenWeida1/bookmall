package com.bookmall.common.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 接口输出用的金额和时间格式。
 * <p>
 * 金额固定保留两位小数并四舍五入，以普通数字字符串返回，避免科学计数法。
 * 时间统一转成 UTC 的 {@code yyyy-MM-dd'T'HH:mm:ss'Z'}。
 */
public final class Texts {

    /** 接口约定的 UTC 时间格式，秒级、无毫秒。 */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private Texts() {
    }

    /**
     * 把金额格式化为两位小数字符串。
     *
     * @param value 原始金额
     * @return 例如 {@code 39.80}
     */
    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 计算单价乘数量后的小计，结果保留两位小数。
     *
     * @param price    单价
     * @param quantity 数量
     * @return 小计金额
     */
    public static BigDecimal times(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 把时间格式化为 UTC 字符串。
     *
     * @param value 带偏移的时间
     * @return 例如 {@code 2026-10-10T08:00:00Z}
     */
    public static String time(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).format(ISO);
    }
}
