package com.gameio.friend;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FriendPresenceConfiguration {
    @Bean
    @ConditionalOnMissingBean(FriendPresenceReader.class)
    FriendPresenceReader offlineFriendPresenceReader() {
        return new OfflineFriendPresenceReader();
    }
}
