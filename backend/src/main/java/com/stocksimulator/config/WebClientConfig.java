package com.stocksimulator.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("yahooFinanceWebClient")
    public WebClient yahooFinanceWebClient(AppProperties appProperties) {
        AppProperties.YahooFinance yfConfig = appProperties.getYahooFinance();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(yfConfig.getMaxInMemorySize()))
                .build();

        return WebClient.builder()
                .baseUrl(yfConfig.getBaseUrl())
                .defaultHeader("User-Agent", yfConfig.getUserAgent())
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();
    }
}
