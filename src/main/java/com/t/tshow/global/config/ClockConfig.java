package com.t.tshow.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** "오늘" 을 서버 시간대가 아니라 서비스 시간대(Asia/Seoul)로 계산하게 하는 시계. 테스트에서는 고정 시계를 쓴다 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(SearchProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}
