package com.abin.chatroom.common.chatai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.deepseek")
public class DeepSeekProperties {

    private boolean use = true;

    private Long uid;

    private String key;

    private String url ;

    private String modelName ;

    private Integer timeout = 60 * 1000;

    private Integer maxTokens = 2048;
}