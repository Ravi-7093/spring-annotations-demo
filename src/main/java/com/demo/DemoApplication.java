package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/*
 * @SpringBootApplication
 *   = @Configuration + @EnableAutoConfiguration + @ComponentScan.
 *   "Start Spring, auto-configure what's on the classpath, and scan this package for beans."
 *
 * @EnableAsync
 *   Turns ON @Async. Without it, @Async methods just run on the caller's thread (silently).
 *
 * @EnableCaching
 *   Turns ON @Cacheable / @CachePut / @CacheEvict. Without it, they are ignored.
 *
 * (No @EnableTransactionManagement needed: Spring Boot enables transactions automatically.)
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
// ci trigger test
