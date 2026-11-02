package com.abin.chatroom.common.chatai.controller;

import com.abin.chatroom.common.chatai.enums.AiToneEnum;
import com.abin.chatroom.common.chatai.service.AiReplyService;
import com.abin.chatroom.common.common.domain.vo.response.ApiResult;
import com.abin.chatroom.common.common.utils.RequestHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/capi/ai")
@Api(tags = "AI高情商回复接口")
@Slf4j
public class AiReplyController {

    @Autowired
    private AiReplyService aiReplyService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @GetMapping("/reply")
    @ApiOperation("AI高情商回复（非流式）")
    public ApiResult<String> aiReply(@RequestParam Long roomId, @RequestParam(defaultValue = "1") Integer toneType) {
        Long uid = RequestHolder.get().getUid();
        AiToneEnum tone = AiToneEnum.of(toneType);
        String reply = aiReplyService.generateReply(roomId, tone);
        return ApiResult.success(reply);
    }

    @GetMapping(value = "/reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("AI高情商回复（流式输出）")
    public SseEmitter aiReplyStream(@RequestParam Long roomId,
                                     @RequestParam(defaultValue = "1") Integer toneType,
                                     HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(300000L);
        Long uid = RequestHolder.get().getUid();
        AiToneEnum tone = AiToneEnum.of(toneType);

        emitter.onCompletion(() -> log.info("SSE连接完成, uid={}, roomId={}", uid, roomId));
        emitter.onTimeout(() -> log.warn("SSE连接超时, uid={}, roomId={}", uid, roomId));
        emitter.onError(e -> log.error("SSE连接错误, uid={}, roomId={}", uid, roomId, e));

        executorService.execute(() -> {
            try {
                aiReplyService.generateReplyStream(roomId, tone, new AiReplyService.StreamCallback() {
                    @Override
                    public void onData(String data) {
                        try {
                            System.out.println("你好呀");
                            emitter.send(SseEmitter.event().name("message").data(data));
                        } catch (IOException e) {
                            log.error("发送SSE数据失败", e);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            log.error("发送SSE完成信号失败", e);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        log.error("AI流式生成失败", e);
                        try {
                            emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                            emitter.completeWithError(e);
                        } catch (IOException ex) {
                            log.error("发送SSE错误信号失败", ex);
                        }
                    }
                });
            } catch (Exception e) {
                log.error("SSE处理异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务器内部错误"));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    log.error("发送错误信息失败", ex);
                }
            }
        });

        return emitter;
    }

    @GetMapping("/tones")
    @ApiOperation("获取可用的语气列表（公开接口）")
    public ApiResult<List<Map<String, Object>>> getTones() {
        List<Map<String, Object>> tones = Arrays.stream(AiToneEnum.values())
                .map(tone -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", tone.getType());
                    map.put("name", tone.getName());
                    map.put("desc", tone.getDesc());
                    return map;
                })
                .collect(Collectors.toList());
        return ApiResult.success(tones);
    }
}
