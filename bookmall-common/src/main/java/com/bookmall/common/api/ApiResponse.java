package com.bookmall.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 各服务统一的接口响应体。
 * <p>
 * 成功时 {@code code} 为 0，{@code message} 为 {@code ok}。失败时 {@code code} 为业务错误码，
 * 与 HTTP 状态分开返回，便于前端按同一结构处理。无业务载荷时 {@code data} 为 {@code null}。
 * 空构造供 JSON 反序列化，全参构造供 {@link #ok(Object)} 和 {@link #fail(int, String)} 组装。
 *
 * @param <T> 业务数据的类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 业务状态码，0 表示成功。 */
    private int code;

    /** 给人看的结果说明。 */
    private String message;

    /** 业务数据；失败且无附加信息时为 null。 */
    private T data;

    /**
     * 构造成功响应。
     *
     * @param data 要返回给调用方的数据
     * @param <T>  数据类型
     * @return code 为 0 的响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /**
     * 构造不含附加数据的失败响应。
     *
     * @param code    业务错误码
     * @param message 错误说明
     * @param <T>     数据类型
     * @return data 为 null 的失败响应
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 构造带附加数据的失败响应，例如库存不足时带回当前库存。
     *
     * @param code    业务错误码
     * @param message 错误说明
     * @param data    失败时仍需返回的上下文
     * @param <T>     数据类型
     * @return 带 data 的失败响应
     */
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
