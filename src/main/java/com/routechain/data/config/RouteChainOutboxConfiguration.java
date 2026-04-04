package com.routechain.data.config;

import com.routechain.config.RouteChainOutboxProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class RouteChainOutboxConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "routechain.outbox", name = "enabled", havingValue = "true")
    public Producer<String, String> routeChainKafkaProducer(RouteChainOutboxProperties properties) {
        Properties kafka = new Properties();
        kafka.put("bootstrap.servers", properties.getBootstrapServers());
        kafka.put("client.id", properties.getClientId());
        kafka.put("acks", "all");
        kafka.put("retries", 0);
        kafka.put("key.serializer", StringSerializer.class.getName());
        kafka.put("value.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(kafka);
    }
}
