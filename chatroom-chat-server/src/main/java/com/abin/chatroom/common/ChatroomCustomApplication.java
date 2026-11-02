package com.abin.chatroom.common;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * @author zhongzb
 * @date 2021/05/27
 */
@SpringBootApplication(scanBasePackages = {"com.abin.chatroom"})
@MapperScan({"com.abin.chatroom.common.**.mapper"})
@ServletComponentScan
public class ChatroomCustomApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatroomCustomApplication.class,args);
    }

}