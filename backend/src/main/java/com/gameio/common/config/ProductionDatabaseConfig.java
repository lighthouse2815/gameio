package com.gameio.common.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionDatabaseConfig {

    @Bean
    DataSource dataSource(Environment environment) {
        String configuredUrl = firstNonBlank(
                environment.getProperty("JDBC_DATABASE_URL"),
                environment.getProperty("DB_URL"),
                environment.getProperty("DATABASE_URL"));
        if (configuredUrl == null) {
            throw new IllegalStateException(
                    "Production requires JDBC_DATABASE_URL, DB_URL, or DATABASE_URL");
        }

        ConnectionDetails details = connectionDetails(configuredUrl);
        String username = firstNonBlank(
                environment.getProperty("DB_USERNAME"),
                environment.getProperty("PGUSER"),
                details.username());
        String password = firstNonBlank(
                environment.getProperty("DB_PASSWORD"),
                environment.getProperty("PGPASSWORD"),
                details.password());

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(details.jdbcUrl());
        if (username != null) dataSource.setUsername(username);
        if (password != null) dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(environment.getProperty("DB_MAX_POOL_SIZE", Integer.class, 10));
        dataSource.setMinimumIdle(environment.getProperty("DB_MIN_IDLE", Integer.class, 1));
        dataSource.setConnectionTimeout(10_000);
        dataSource.setValidationTimeout(5_000);
        return dataSource;
    }

    private ConnectionDetails connectionDetails(String configuredUrl) {
        if (configuredUrl.startsWith("jdbc:")) {
            return new ConnectionDetails(configuredUrl, null, null);
        }
        if (!configuredUrl.startsWith("postgres://") && !configuredUrl.startsWith("postgresql://")) {
            throw new IllegalStateException("DATABASE_URL must use a PostgreSQL or JDBC PostgreSQL scheme");
        }

        URI uri = URI.create(configuredUrl.replaceFirst("^postgres://", "postgresql://"));
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            jdbcUrl += "?" + uri.getRawQuery();
        }
        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            username = decode(credentials[0]);
            password = credentials.length == 2 ? decode(credentials[1]) : null;
        }
        return new ConnectionDetails(jdbcUrl, username, password);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private record ConnectionDetails(String jdbcUrl, String username, String password) {
    }
}
