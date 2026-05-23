package com.roshan.Url.Shortener.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ShardDataSourceConfig {

    @Value("${MYSQL_HOST:localhost}")
    private String mysqlHost;

    @Value("${DB_USERNAME:root}")
    private String username;

    @Value("${MYSQL_ROOT_PASSWORD}")
    private String password;

    @Value("${MYSQL_DATABASE:url_shortener}")
    private String database;

    @Value("${MYSQL_SHARD_0_PORT:3308}")
    private int shard0Port;

    @Value("${MYSQL_SHARD_1_PORT:3309}")
    private int shard1Port;

    @Bean
    public Map<Integer, DataSource> shardDataSources() {
        Map<Integer, DataSource> shards = new HashMap<>();

        shards.put(0, buildDataSource(shard0Port));
        shards.put(1, buildDataSource(shard1Port));

        return shards;
    }

    private DataSource buildDataSource(int port) {
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                mysqlHost, port, database);

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }
}
