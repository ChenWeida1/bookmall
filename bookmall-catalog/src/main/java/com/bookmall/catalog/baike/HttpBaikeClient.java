package com.bookmall.catalog.baike;

import com.bookmall.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * 调用百度百科名片接口。超时 3 秒。appid 用公开示例，不记录响应正文以外的地址参数。
 */
@Component
@Profile("!test")
public class HttpBaikeClient implements BaikeClient {

    private final RestClient client;

    private final String appId;

    public HttpBaikeClient(@Value("${bookmall.baike.app-id:379020}") String appId) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder()
                .baseUrl("https://baike.baidu.com")
                .requestFactory(factory)
                .build();
        this.appId = appId;
    }

    @Override
    public BaikeCard lookup(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://baike.baidu.com/api/openapi/BaikeLemmaCardApi")
                .queryParam("scope", 103)
                .queryParam("format", "json")
                .queryParam("appid", appId)
                .queryParam("bk_key", keyword.trim())
                .queryParam("bk_length", 600)
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
