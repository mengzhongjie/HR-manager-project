package com.wangmian.hr_manager_project.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 配置：解决 MongoDB ObjectId 序列化为扩展 JSON ({$oid:...}) 的问题
 * 只注册一个 Module Bean，由 Spring Boot 自动配置集成到 ObjectMapper
 * 不覆盖默认 ObjectMapper，从而保留 JSR310 等自动支持
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule objectIdModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(ObjectId.class, new JsonSerializer<>() {
            @Override
            public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.toHexString());
            }
        });
        return module;
    }
}
