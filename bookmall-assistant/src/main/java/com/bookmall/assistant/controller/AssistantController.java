package com.bookmall.assistant.controller;

import com.bookmall.assistant.service.AssistantService;
import com.bookmall.assistant.service.SynopsisService;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.auth.JwtAuthFilter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 查书客服接口。用户编号只从令牌读取。
 */
@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    private final SynopsisService synopsisService;

    /** 当前用户的对话历史。 */
    @GetMapping("/api/assistant/messages")
    public ApiResponse<PageResult<Map<String, Object>>> messages(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(assistantService.history(JwtAuthFilter.userId(request), page, size));
    }

    /**
     * 流式提问。未配置时在写出事件流之前返回 JSON 业务错误。
     */
    @PostMapping(value = "/api/assistant/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
        return assistantService.ask(JwtAuthFilter.userId(request), body);
    }

    /** 管理端生成梗概。没密钥时 50301。 */
    @PostMapping("/api/assistant/books/{id}/synopsis")
    public ApiResponse<Map<String, String>> synopsis(HttpServletRequest request, @PathVariable String id) {
        JwtAuthFilter.requireAdmin(request);
        long bookId;
        try {
            bookId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw com.bookmall.common.error.BusinessException.notFound("图书不存在");
        }
        return ApiResponse.ok(Map.of("synopsis", synopsisService.ensureOrThrow(bookId)));
    }
}
