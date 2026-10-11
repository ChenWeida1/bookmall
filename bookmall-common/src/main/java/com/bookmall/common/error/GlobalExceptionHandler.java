package com.bookmall.common.error;

import com.bookmall.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 把异常统一转成 {@link ApiResponse}。
 * <p>
 * 业务异常保留原有状态码和错误码。请求体、路径或查询参数无法解析时一律按参数不合法处理。
 * 其余未预期异常只记日志，对外返回固定的服务器内部错误，避免把堆栈暴露给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理可预期的业务失败。
     *
     * @param ex 业务异常
     * @return 与异常中 HTTP 状态一致的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> business(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage(), ex.getData()));
    }

    /**
     * 处理请求参数无法绑定或请求体无法读取的情况。
     *
     * @param ex 绑定或解析异常
     * @return HTTP 400，业务码 40001
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Object>> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(40001, "参数不合法"));
    }

    /**
     * 兜底处理未分类异常。
     *
     * @param ex 未预期异常
     * @return HTTP 500，业务码 50000
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> unexpected(Exception ex) {
        log.error("未预期错误", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.fail(50000, "服务器内部错误"));
    }
}
