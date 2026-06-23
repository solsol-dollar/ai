package com.shinhan.eclipse.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class PgVectorDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.pgvector")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Qualifier("pgVectorDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .build();
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(
            @Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
