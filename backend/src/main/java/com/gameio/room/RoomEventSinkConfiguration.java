package com.gameio.room;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RoomEventSinkConfiguration {
    @Bean
    @ConditionalOnMissingBean(RoomEventSink.class)
    RoomEventSink noOpRoomEventSink() {
        return new RoomEventSink() {
            @Override
            public void roomUpdated(RoomState room) {
            }

            @Override
            public void gameStarted(RoomState room) {
            }

            @Override
            public void playerDisconnected(RoomState room, UUID userId) {
            }
        };
    }
}
