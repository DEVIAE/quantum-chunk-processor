package com.quantum.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantum.common.config.JacksonConfig;
import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

@Configuration
public class JmsConfig {

    @Value("${processor.consumer.prefetch:10}")
    private int prefetchSize;

    @Value("${processor.consumer.concurrency:1-5}")
    private String concurrency;

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();

        CachingConnectionFactory cachingFactory = new CachingConnectionFactory();
        cachingFactory.setTargetConnectionFactory(connectionFactory);
        cachingFactory.setSessionCacheSize(20);

        factory.setConnectionFactory(cachingFactory);
        factory.setConcurrency(concurrency);
        factory.setSessionTransacted(true);
        factory.setErrorHandler(t ->
                org.slf4j.LoggerFactory.getLogger("JmsErrorHandler")
                        .error("JMS error: {}", t.getMessage(), t));
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDeliveryPersistent(true);
        template.setSessionTransacted(true);
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JacksonConfig.createObjectMapper();
    }
}
