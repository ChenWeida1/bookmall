package com.bookmall.gateway.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 从 classpath 加载网关流控规则。不用 Dashboard，也不依赖本机文件路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelFlowRulesLoader {

    private final ObjectMapper objectMapper;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("sentinel/flow-rules.json").getInputStream()) {
            List<FlowRule> rules = objectMapper.readValue(in, new TypeReference<>() {
            });
            FlowRuleManager.loadRules(rules);
            log.info("已加载 Sentinel 流控规则 {} 条", rules.size());
        } catch (Exception ex) {
            throw new IllegalStateException("加载 sentinel/flow-rules.json 失败", ex);
        }
    }
}
