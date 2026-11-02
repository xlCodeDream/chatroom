package com.abin.chatroom.common.chatai.service;

import com.abin.chatroom.common.chatai.enums.AiToneEnum;

public interface AiReplyService {
    String generateReply(Long roomId, AiToneEnum tone);

    void generateReplyStream(Long roomId, AiToneEnum tone, StreamCallback callback);

    interface StreamCallback {
        void onData(String data);
        void onComplete();
        void onError(Throwable e);
    }
}