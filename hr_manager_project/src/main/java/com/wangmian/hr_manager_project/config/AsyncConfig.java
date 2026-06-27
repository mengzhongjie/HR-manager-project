package com.wangmian.hr_manager_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    @Bean("positionEventExecutor")
    public Executor positionEventExecutor() {
        return new SimpleAsyncTaskExecutor("position-event-");
    }
}
