package com.gameio;

import com.gameio.common.security.JwtProperties;
import com.gameio.common.security.RefreshCookieProperties;
import com.gameio.common.web.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, RefreshCookieProperties.class, CorsProperties.class})
public class GameioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameioApplication.class, args);
    }
}
