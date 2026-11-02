package com.abin.chatroom.common.chatai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public enum AiToneEnum {
    NAUGHTY(1, "俏皮", "以轻松幽默、俏皮可爱的语气回复，适合活跃气氛"),
    SERIOUS(2, "严肃", "以正式严肃的语气回复，适合正式场合"),
    KIND(3, "温柔", "以温柔体贴的语气回复，充满关怀"),
    SARCASM(4, "幽默", "以幽默风趣、略带调侃的语气回复"),
    PROFESSIONAL(5, "专业", "以专业客观的语气回复，提供专业见解"),
    ;

    private final Integer type;
    private final String name;
    private final String desc;

    private static Map<Integer, AiToneEnum> cache;

    static {
        cache = Arrays.stream(AiToneEnum.values()).collect(Collectors.toMap(AiToneEnum::getType, Function.identity()));
    }

    public static AiToneEnum of(Integer type) {
        return cache.getOrDefault(type, NAUGHTY);
    }
}
