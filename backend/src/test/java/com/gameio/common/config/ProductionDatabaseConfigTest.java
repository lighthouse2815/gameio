package com.gameio.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionDatabaseConfigTest {
    @Test
    void convertsRailwayStylePostgresUrlAndDecodesCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://gameio:p%40ss@postgres.internal:5433/gameio?sslmode=require");

        HikariDataSource dataSource = (HikariDataSource) new ProductionDatabaseConfig().dataSource(environment);
        try {
            assertThat(dataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://postgres.internal:5433/gameio?sslmode=require");
            assertThat(dataSource.getUsername()).isEqualTo("gameio");
            assertThat(dataSource.getPassword()).isEqualTo("p@ss");
        } finally {
            dataSource.close();
        }
    }
}
