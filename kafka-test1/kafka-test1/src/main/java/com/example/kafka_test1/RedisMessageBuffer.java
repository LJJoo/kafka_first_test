package com.example.kafka_test1;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessageBuffer {
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageBuffer.class);
    private static final String KEY = "order:buffer";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void push(String json) {
        redisTemplate.opsForList().rightPush(KEY, json);
        logger.info("redis buffer push, current size {}", size());
    }

    public String pop() {
        return redisTemplate.opsForList().leftPop(KEY);
    }

    public long size() {
        Long s = redisTemplate.opsForList().size(KEY);
        return s != null ? s : 0;
    }

    public List<String> getAll() {
        long s = size();
        if (s == 0) return List.of();
        return redisTemplate.opsForList().range(KEY, 0, s - 1);
    }

    public void clear() {
        redisTemplate.delete(KEY);
    }
}
