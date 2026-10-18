package com.bookmall.catalog.baike;

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
 */
@Component
@Profile("!test")
@RefreshScope
public class HttpBaikeClient implements BaikeClient {

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
