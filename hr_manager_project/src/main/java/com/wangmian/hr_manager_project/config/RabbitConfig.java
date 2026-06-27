package com.wangmian.hr_manager_project.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // ===== 岗位变更 Exchange（已有） =====
    public static final String POSITION_EXCHANGE = "hr.position.exchange";
    public static final String POSITION_QUEUE = "hr.position.queue";
    public static final String POSITION_KEY = "hr.position.changed";

    // ===== 面试状态通知 =====
    public static final String INTERVIEW_EXCHANGE = "hr.interview.exchange";
    public static final String INTERVIEW_QUEUE = "hr.interview.queue";
    public static final String INTERVIEW_KEY = "hr.interview.status";

    // ===== 简历提交队列 =====
    public static final String RESUME_EXCHANGE = "hr.resume.exchange";
    public static final String RESUME_QUEUE = "hr.resume.queue";
    public static final String RESUME_DLQ = "hr.resume.dlq";
    public static final String RESUME_KEY = "hr.resume.submitted";
    public static final int BATCH_SIZE = 10;
    public static final long BATCH_INTERVAL_MS = 5000;

    // ========== 岗位变更（已有） ==========

    @Bean
    public TopicExchange positionExchange() {
        return new TopicExchange(POSITION_EXCHANGE);
    }

    @Bean
    public Queue positionQueue() {
        return new Queue(POSITION_QUEUE, true);
    }

    @Bean
    public Binding positionBinding() {
        return BindingBuilder.bind(positionQueue()).to(positionExchange()).with(POSITION_KEY);
    }

    // ========== 面试状态通知 ==========

    @Bean
    public TopicExchange interviewExchange() {
        return new TopicExchange(INTERVIEW_EXCHANGE);
    }

    @Bean
    public Queue interviewQueue() {
        return new Queue(INTERVIEW_QUEUE, true);
    }

    @Bean
    public Binding interviewBinding() {
        return BindingBuilder.bind(interviewQueue()).to(interviewExchange()).with(INTERVIEW_KEY);
    }

    // ========== 简历提交 ==========

    @Bean
    public TopicExchange resumeExchange() {
        return new TopicExchange(RESUME_EXCHANGE);
    }

    /** 死信队列 */
    @Bean
    public Queue resumeDlq() {
        return new Queue(RESUME_DLQ, true);
    }

    /** 主队列：失败3次后进入死信队列 */
    @Bean
    public Queue resumeQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", RESUME_EXCHANGE);
        args.put("x-dead-letter-routing-key", "dlq");
        args.put("x-message-ttl", 60000); // 消息TTL 60秒
        return new Queue(RESUME_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding resumeBinding() {
        return BindingBuilder.bind(resumeQueue()).to(resumeExchange()).with(RESUME_KEY);
    }

    @Bean
    public Binding resumeDlqBinding() {
        return BindingBuilder.bind(resumeDlq()).to(resumeExchange()).with("dlq");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}
