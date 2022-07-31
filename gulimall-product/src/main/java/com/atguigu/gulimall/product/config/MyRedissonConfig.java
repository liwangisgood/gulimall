package com.atguigu.gulimall.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MyRedissonConfig {

    /**
     * 所有对redisson的使用都是通过redissonclient
     * @return
     * @throws IOException
     */
    @Bean(destroyMethod="shutdown")
    public RedissonClient redisson() throws IOException {
        //1.创建配置
        //Redis url should start with redis:// or rediss:// (for SSL connection)
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        //2.根据config创建出实列
        return Redisson.create(config);
    }
}
