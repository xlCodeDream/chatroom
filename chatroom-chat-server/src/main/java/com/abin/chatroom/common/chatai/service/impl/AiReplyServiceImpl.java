package com.abin.chatroom.common.chatai.service.impl;

import com.abin.chatroom.common.chat.dao.MessageDao;
import com.abin.chatroom.common.chat.domain.entity.Message;
import com.abin.chatroom.common.chatai.domain.ChatGPTMsg;
import com.abin.chatroom.common.chatai.domain.builder.ChatGPTMsgBuilder;
import com.abin.chatroom.common.chatai.enums.AiToneEnum;
import com.abin.chatroom.common.chatai.properties.ChatGPTProperties;
import com.abin.chatroom.common.chatai.properties.DeepSeekProperties;
import com.abin.chatroom.common.chatai.service.AiReplyService;
import com.abin.chatroom.common.chatai.utils.ChatGPTUtils;
import com.abin.chatroom.common.user.domain.vo.response.user.UserInfoResp;
import com.abin.chatroom.common.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiReplyServiceImpl implements AiReplyService {

    private static final int HISTORY_COUNT = 50;
    private static final int MAX_REPLY_LENGTH = 50;

    @Autowired
    private ChatGPTProperties chatGPTProperties;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private MessageDao messageDao;

    @Autowired
    private UserService userService;

    @Override
    public String generateReply(Long roomId, AiToneEnum tone) {
//        if (!deepSeekProperties.isUse() && !chatGPTProperties.isUse()) {
//            return "AI功能未开启";
//        }

        List<Message> history = getMessageHistory(roomId);
        if (history.isEmpty()) {
            return "这个群还没有聊天记录呢~";
        }

        String historyText = formatHistory(history);
        String reply;
        if (deepSeekProperties.isUse()) {
            reply = callDeepSeek(historyText, tone);
        } else {
            reply = callAI(historyText, tone);
        }
        if (reply.length() > MAX_REPLY_LENGTH) {
            reply = reply.substring(0, MAX_REPLY_LENGTH);
        }
        log.info("AI回复生成成功, roomId={}, tone={}, length={}", roomId, tone.getName(), reply.length());
        return reply;
    }

    @Override
    public void generateReplyStream(Long roomId, AiToneEnum tone, StreamCallback callback) {
//        if (!deepSeekProperties.isUse() && !chatGPTProperties.isUse()) {
//            callback.onData("AI功能未开启");
//            callback.onComplete();
//            return;
//        }

        List<Message> history = getMessageHistory(roomId);
        if (history.isEmpty()) {
            callback.onData("这个群还没有聊天记录呢~");
            callback.onComplete();
            return;
        }

        String historyText = formatHistory(history);
        if (deepSeekProperties.isUse()) {
            callDeepSeekStream(historyText, tone, callback);
        } else {
            callAIStream(historyText, tone, callback);
        }
    }

    private List<Message> getMessageHistory(Long roomId) {
        return messageDao.lambdaQuery()
                .eq(Message::getRoomId, roomId)
                .orderByDesc(Message::getId)
                .last("LIMIT " + HISTORY_COUNT)
                .list()
                .stream()
                .collect(Collectors.toList());
    }

    private String formatHistory(List<Message> history) {
        StringBuilder sb = new StringBuilder();
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            UserInfoResp user = userService.getUserInfo(msg.getFromUid());
            String userName = user != null ? user.getName() : "未知用户";
            sb.append(userName).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String callAI(String historyText, AiToneEnum tone) {
        String systemPrompt = String.format("你是一个高情商聊天助手，需要根据群聊对话历史生成一句合适的回复。\n\n回复要求：\n1. 语气风格：%s\n2. 语气说明：%s\n3. 回复要符合当前对话上下文，自然融入群聊\n4. 回复长度必须在%d字以内\n5. 只返回回复内容，不要包含其他说明文字", tone.getName(), tone.getDesc(), MAX_REPLY_LENGTH);

        String userPrompt = String.format("群聊对话历史如下：\n\n%s\n\n请根据以上对话历史，生成一句合适的回复：", historyText);

        List<ChatGPTMsg> messages = new ArrayList<>();
        ChatGPTMsg systemMsg = new ChatGPTMsg();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);
        messages.add(ChatGPTMsgBuilder.userMsg(userPrompt));

        try {
            Response response = ChatGPTUtils.create(chatGPTProperties.getKey())
                    .proxyUrl(chatGPTProperties.getProxyUrl())
                    .model(chatGPTProperties.getModelName())
                    .timeout(chatGPTProperties.getTimeout())
                    .maxTokens(chatGPTProperties.getMaxTokens())
                    .message(messages)
                    .send();
            return ChatGPTUtils.parseText(response);
        } catch (Exception e) {
            log.error("AI调用失败", e);
            return "系统开小差啦~~";
        }
    }

    private void callAIStream(String historyText, AiToneEnum tone, StreamCallback callback) {
        String systemPrompt = String.format("你是一个高情商聊天助手，需要根据群聊对话历史生成一句合适的回复。\n\n回复要求：\n1. 语气风格：%s\n2. 语气说明：%s\n3. 回复要符合当前对话上下文，自然融入群聊\n4. 回复长度必须在%d字以内\n5. 只返回回复内容，不要包含其他说明文字", tone.getName(), tone.getDesc(), MAX_REPLY_LENGTH);

        String userPrompt = String.format("群聊对话历史如下：\n\n%s\n\n请根据以上对话历史，生成一句合适的回复：", historyText);

        List<ChatGPTMsg> messages = new ArrayList<>();
        ChatGPTMsg systemMsg = new ChatGPTMsg();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);
        messages.add(ChatGPTMsgBuilder.userMsg(userPrompt));

        try {
            Response response = ChatGPTUtils.create(chatGPTProperties.getKey())
                    .proxyUrl(chatGPTProperties.getProxyUrl())
                    .model(chatGPTProperties.getModelName())
                    .timeout(chatGPTProperties.getTimeout())
                    .maxTokens(chatGPTProperties.getMaxTokens())
                    .message(messages)
                    .send();

            ResponseBody body = response.body();
            if (body == null) {
                callback.onError(new IOException("ResponseBody is null"));
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
            String line;
            int totalLength = 0;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) {
                    break;
                }

                String content = ChatGPTUtils.parseDelta(data);
                if (content != null && !content.isEmpty()) {
                    if (totalLength + content.length() > MAX_REPLY_LENGTH) {
                        int remaining = MAX_REPLY_LENGTH - totalLength;
                        if (remaining > 0) {
                            callback.onData(content.substring(0, remaining));
                        }
                        break;
                    }
                    callback.onData(content);
                    totalLength += content.length();
                }
            }

            reader.close();
            callback.onComplete();
        } catch (Exception e) {
            log.error("AI流式调用失败", e);
            callback.onError(e);
        }
    }

    private String callDeepSeek(String historyText, AiToneEnum tone) {
        String systemPrompt = String.format("你是一个高情商聊天助手，需要根据群聊对话历史生成一句合适的回复。\n\n回复要求：\n1. 语气风格：%s\n2. 语气说明：%s\n3. 回复要符合当前对话上下文，自然融入群聊\n4. 回复长度必须在%d字以内\n5. 只返回回复内容，不要包含其他说明文字", tone.getName(), tone.getDesc(), MAX_REPLY_LENGTH);

        String userPrompt = String.format("群聊对话历史如下：\n\n%s\n\n请根据以上对话历史，生成一句合适的回复：", historyText);

        List<ChatGPTMsg> messages = new ArrayList<>();
        ChatGPTMsg systemMsg = new ChatGPTMsg();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);
        messages.add(ChatGPTMsgBuilder.userMsg(userPrompt));

        try {
            Response response = ChatGPTUtils.create(deepSeekProperties.getKey())
                    .proxyUrl(deepSeekProperties.getUrl())
                    .model(deepSeekProperties.getModelName())
                    .timeout(deepSeekProperties.getTimeout())
                    .maxTokens(deepSeekProperties.getMaxTokens())
                    .message(messages)
                    .send();
            return ChatGPTUtils.parseText(response);
        } catch (Exception e) {
            log.error("DeepSeek调用失败", e);
            return "系统开小差啦~~";
        }
    }

    private void callDeepSeekStream(String historyText, AiToneEnum tone, StreamCallback callback) {
        String systemPrompt = String.format("你是一个高情商聊天助手，需要根据群聊对话历史生成一句合适的回复。\n\n回复要求：\n1. 语气风格：%s\n2. 语气说明：%s\n3. 回复要符合当前对话上下文，自然融入群聊\n4. 回复长度必须在%d字以内\n5. 只返回回复内容，不要包含其他说明文字", tone.getName(), tone.getDesc(), MAX_REPLY_LENGTH);

        String userPrompt = String.format("群聊对话历史如下：\n\n%s\n\n请根据以上对话历史，生成一句合适的回复：", historyText);

        List<ChatGPTMsg> messages = new ArrayList<>();
        ChatGPTMsg systemMsg = new ChatGPTMsg();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);
        messages.add(ChatGPTMsgBuilder.userMsg(userPrompt));

        try {
            Response response = ChatGPTUtils.create(deepSeekProperties.getKey())
                    .proxyUrl(deepSeekProperties.getUrl())
                    .model(deepSeekProperties.getModelName())
                    .timeout(deepSeekProperties.getTimeout())
                    .maxTokens(deepSeekProperties.getMaxTokens())
                    .message(messages)
                    .send();

            ResponseBody body = response.body();
            if (body == null) {
                callback.onError(new IOException("ResponseBody is null"));
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
            String line;
            int totalLength = 0;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) {
                    break;
                }

                String content = ChatGPTUtils.parseDelta(data);
                if (content != null && !content.isEmpty()) {
                    if (totalLength + content.length() > MAX_REPLY_LENGTH) {
                        int remaining = MAX_REPLY_LENGTH - totalLength;
                        if (remaining > 0) {
                            callback.onData(content.substring(0, remaining));
                        }
                        break;
                    }
                    callback.onData(content);
                    totalLength += content.length();
                }
            }

            reader.close();
            callback.onComplete();
        } catch (Exception e) {
            log.error("DeepSeek流式调用失败", e);
            callback.onError(e);
        }
    }
}