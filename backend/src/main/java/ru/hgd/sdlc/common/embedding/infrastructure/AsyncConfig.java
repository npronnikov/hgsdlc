package ru.hgd.sdlc.common.embedding.infrastructure;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "embeddingTaskExecutor")
    public Executor embeddingTaskExecutor(EmbeddingProperties embeddingProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(embeddingProperties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(embeddingProperties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(embeddingProperties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix("embedding-async-");
        executor.initialize();
        return executor;
    }
}
