package com.dating.post;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync  // 开启异步任务能力
@EnableScheduling  // 开启定时任务
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")  // 开启 ShedLock 多实例互斥
public class PostApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}
