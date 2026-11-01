package com.bookmall.catalog.baike;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.bookmall.catalog.config.BaikeProperties;
import com.bookmall.common.error.BusinessException;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 调用百度百科名片接口。超时和地址来自配置。刷新后按新超时重建客户端。
 * appid 用公开示例，不记录响应正文以外的地址参数。
 * <p>
 * {@code baike-lookup} 走 Sentinel 异常比例熔断；熔断打开时与连不上一样返回 50302。
 * 用 {@link SphU} 手写入境，避免经 {@link BaikeClient} 接口代理时注解切面失效。
 */
@Component
@Profile("!test")
@RefreshScope
public class HttpBaikeClient implements BaikeClient {

    public static final String RESOURCE = "baike-lookup";

    private final RestClient client;

    private final BaikeProperties baike;

    public HttpBaikeClient(BaikeProperties baike) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(baike.getTimeout());
        factory.setReadTimeout(baike.getTimeout());
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baike = baike;
    }

    @Override
    public BaikeCard lookup(String keyword) {
        Entry entry = null;
        try {
            entry = SphU.entry(RESOURCE);
            return doLookup(keyword);
        } catch (BlockException ex) {
            throw new BusinessException(503, 50302, "外部书目暂时不可用");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private BaikeCard doLookup(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(baike.getCardUrl())
                .queryParam("scope", 103)
                .queryParam("format", "json")
                .queryParam("appid", baike.getAppId())
                .queryParam("bk_key", keyword.trim())
                .queryParam("bk_length", baike.getExcerptLength())
                .build()
                .encode()
                .toUri();
        try {
            String body = client.get().uri(uri).retrieve().body(String.class);
            return BaikeCards.parse(body);
        } catch (RestClientException ex) {
            throw new BusinessException(503, 50302, "外部书目暂时不可用");
        }
    }
}
