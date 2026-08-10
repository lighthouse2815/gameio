package com.gameio.multiplayer;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RealtimePublisherConfiguration {
    @Bean
    @ConditionalOnMissingBean(RealtimePublisher.class)
    RealtimePublisher noOpRealtimePublisher() {
        return new RealtimePublisher() {
            @Override
            public void toUser(UUID userId, String type, UUID roomId, Object payload, String requestId) {
            }

            @Override
            public void toRoom(UUID roomId, String type, Object payload, String requestId) {
            }
        };
    }
}
