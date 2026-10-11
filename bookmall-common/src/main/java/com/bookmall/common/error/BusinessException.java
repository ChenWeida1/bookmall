package com.bookmall.common.error;

/**
 * 可预期的业务异常。
 * <p>
 * 控制器不直接捕获它，由 {@link GlobalExceptionHandler} 转成对应的 HTTP 状态和 {@code ApiResponse}。
 * {@code httpStatus} 是协议层状态，{@code code} 是业务错误码，两者可以不同。
 * {@code data} 用来带回失败上下文，例如库存不足时的剩余数量。
 */
public class BusinessException extends RuntimeException {

    /** HTTP 状态码，例如 400、401、404。 */
    private final int httpStatus;

    /** 业务错误码，例如 40001、40006。 */
    private final int code;

    /** 失败时附带的上下文，没有则为 null。 */
    private final Object data;

    /**
     * 构造不含附加数据的业务异常。
     *
     * @param httpStatus HTTP 状态码
     * @param code       业务错误码
     * @param message    错误说明
     */
    public BusinessException(int httpStatus, int code, String message) {
        this(httpStatus, code, message, null);
    }

    /**
     * 构造带附加数据的业务异常。
     *
     * @param httpStatus HTTP 状态码
     * @param code       业务错误码
     * @param message    错误说明
     * @param data       失败上下文
     */
    public BusinessException(int httpStatus, int code, String message, Object data) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.data = data;
    }

    /** @return HTTP 状态码 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** @return 业务错误码 */
    public int getCode() {
        return code;
    }

    /** @return 失败上下文 */
    public Object getData() {
        return data;
    }

    /**
     * 参数不合法，错误码 40001。
     *
     * @param message 具体原因
     * @return HTTP 400 的业务异常
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, 40001, message);
    }

    /**
     * 未登录或令牌失效，错误码 40100。
     *
     * @return HTTP 401 的业务异常
     */
    public static BusinessException unauthorized() {
        return new BusinessException(401, 40100, "未登录或登录已失效");
    }

    /**
     * 资源不存在，错误码 40400。
     *
     * @param message 说明缺失的是哪类资源
     * @return HTTP 404 的业务异常
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, 40400, message);
    }

    /**
     * 已登录但无权限，错误码 40300。
     *
     * @param message 说明原因，例如「需要管理员权限」
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, 40300, message);
    }
}
