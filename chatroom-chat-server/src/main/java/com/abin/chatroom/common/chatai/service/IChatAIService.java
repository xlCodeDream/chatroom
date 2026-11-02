package com.abin.chatroom.common.chatai.service;

import com.abin.chatroom.common.chat.domain.entity.Message;

public interface IChatAIService {

    void chat(Message message);
}
