package com.restaurantqr.sse.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub wiring for SSE event fan-out.
 * <p>
 * Only activated when {@code spring.data.redis.host} is set.
 * Registers a {@link RedisMessageListenerContainer} that subscribes to
 * {@code sse:restaurant-events} and delegates to {@link RedisEventSubscriber}.
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisSseConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisSseConfig.class);

    @Bean
    public ChannelTopic sseChannelTopic() {
        return new ChannelTopic(RedisEventBridge.CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisEventSubscriber subscriber,
            ChannelTopic sseChannelTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, sseChannelTopic);

        log.info("Redis SSE listener container configured — subscribing to channel '{}'",
                sseChannelTopic.getTopic());

        return container;
    }
}

