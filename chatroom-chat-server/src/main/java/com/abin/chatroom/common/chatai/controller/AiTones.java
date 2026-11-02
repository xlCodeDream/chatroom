package com.abin.chatroom.common.chatai.controller;

import com.abin.chatroom.common.chatai.enums.AiToneEnum;
import com.abin.chatroom.common.common.domain.vo.response.ApiResult;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping ("/ai")
public class AiTones {
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
