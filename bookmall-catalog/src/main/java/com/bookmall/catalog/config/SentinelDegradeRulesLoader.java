package com.bookmall.catalog.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
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
 * 从 classpath 加载百科熔断规则。无 Dashboard。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelDegradeRulesLoader {

    private final ObjectMapper objectMapper;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("sentinel/degrade-rules.json").getInputStream()) {
            List<DegradeRule> rules = objectMapper.readValue(in, new TypeReference<>() {
            });
            DegradeRuleManager.loadRules(rules);
            log.info("已加载 Sentinel 熔断规则 {} 条", rules.size());
        } catch (Exception ex) {
            throw new IllegalStateException("加载 sentinel/degrade-rules.json 失败", ex);
        }
    }
}
