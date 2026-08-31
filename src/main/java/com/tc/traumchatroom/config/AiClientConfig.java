package com.tc.traumchatroom.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * AI HTTP 客户端配置
 *
 * 为什么单独做成 Bean：原先在 AiServiceImpl 的方法体内 WebClient.builder()...build()，
 * 每次 AI 调用都新建一个 WebClient，连带新建底层 HttpClient 与连接池 ——
 * TCP 连接和 TLS 握手完全无法复用，每次提问都要重新建连。
 * 提为单例后连接池在多次调用间复用。
 *
 * 放在独立的 @Configuration 而不是塞进 AiConfig：AiConfig 是 @ConfigurationProperties，
 * 把 @Bean 方法混进去会让它被 CGLIB 代理，语义上也不该由配置载体去造客户端。
 * 这里以方法参数注入 AiConfig，Spring 保证注入时其属性已绑定完成。
 */
@Configuration
public class AiClientConfig {

    /**
     * AI 调用专用 WebClient（单例，连接池复用）。
     *
     * baseUrl 与请求头与原实现保持一致；超时仍由调用方按 aiConfig.getTimeout() 逐次施加
     * （Mono.timeout），这里额外补上连接与读超时，避免连接层卡死时把线程长期占住。
     */
    @Bean
    public WebClient aiWebClient(AiConfig aiConfig) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, aiConfig.getTimeout())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(aiConfig.getTimeout(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(aiConfig.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.getKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
