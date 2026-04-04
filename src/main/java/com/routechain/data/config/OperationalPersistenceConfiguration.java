package com.routechain.data.config;

import com.routechain.data.jdbc.JdbcOfferStateStore;
import com.routechain.data.jdbc.JdbcOperationalPersistenceAdapter;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.DriverPresenceStore;
import com.routechain.data.port.IdempotencyRepository;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.port.OrderRepository;
import com.routechain.data.port.OutboxRepository;
import com.routechain.data.port.QuoteRepository;
import com.routechain.data.port.WalletRepository;
import com.routechain.data.redis.RedisDriverPresenceStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(RouteChainPersistenceProperties.class)
public class OperationalPersistenceConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public DataSource routeChainDataSource(RouteChainPersistenceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbc().getUrl());
        config.setUsername(properties.getJdbc().getUsername());
        config.setPassword(properties.getJdbc().getPassword());
        config.setDriverClassName(properties.getJdbc().getDriverClassName());
        config.setMaximumPoolSize(properties.getJdbc().getMaxPoolSize());
        config.setMinimumIdle(properties.getJdbc().getMinIdle());
        config.setConnectionTimeout(properties.getJdbc().getConnectionTimeoutMs());
        config.setPoolName("routechain-jdbc");
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public Flyway routeChainFlyway(DataSource routeChainDataSource) {
        return Flyway.configure()
                .dataSource(routeChainDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public NamedParameterJdbcTemplate routeChainJdbcTemplate(DataSource routeChainDataSource, Flyway ignored) {
        return new NamedParameterJdbcTemplate(routeChainDataSource);
    }

    @Bean("transactionManager")
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public PlatformTransactionManager jdbcTransactionManager(DataSource routeChainDataSource) {
        return new DataSourceTransactionManager(routeChainDataSource);
    }

    @Bean("transactionManager")
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "false", matchIfMissing = true)
    public PlatformTransactionManager noOpTransactionManager() {
        return new NoOpTransactionManager();
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public JdbcOperationalPersistenceAdapter jdbcOperationalPersistenceAdapter(NamedParameterJdbcTemplate routeChainJdbcTemplate) {
        return new JdbcOperationalPersistenceAdapter(routeChainJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public JdbcOfferStateStore jdbcOfferStateStore(NamedParameterJdbcTemplate routeChainJdbcTemplate) {
        return new JdbcOfferStateStore(routeChainJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public OrderRepository orderRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public QuoteRepository quoteRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public DriverFleetRepository driverFleetRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public WalletRepository walletRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public IdempotencyRepository idempotencyRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public OutboxRepository outboxRepository(JdbcOperationalPersistenceAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "true")
    public OfferStateStore offerStateStore(JdbcOfferStateStore adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.redis", name = "enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory(RouteChainPersistenceProperties properties) {
        RouteChainPersistenceProperties.Redis redis = properties.getRedis();
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redis.getHost(), redis.getPort());
        configuration.setDatabase(redis.getDatabase());
        if (redis.getPassword() != null && !redis.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(redis.getPassword()));
        }
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redis.getConnectTimeoutMs()))
                .shutdownTimeout(Duration.ZERO)
                .build();
        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @Bean("stringRedisTemplate")
    @ConditionalOnProperty(prefix = "routechain.persistence.redis", name = "enabled", havingValue = "true")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "routechain.persistence.redis", name = "enabled", havingValue = "true")
    public DriverPresenceStore driverPresenceStore(StringRedisTemplate stringRedisTemplate,
                                                   RouteChainPersistenceProperties properties) {
        RouteChainPersistenceProperties.Redis redis = properties.getRedis();
        return new RedisDriverPresenceStore(
                stringRedisTemplate,
                redis.getKeyPrefix(),
                redis.getDriverPresenceTtl()
        );
    }
}
