package com.abin.chatroom.common.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.abin.chatroom.common.chat.domain.enums.MessageTypeEnum;
import com.abin.chatroom.common.chat.domain.vo.request.ChatMessageReq;
import com.abin.chatroom.common.chat.domain.vo.request.msg.TextMsgReq;
import com.abin.chatroom.common.chat.service.ChatService;
import com.abin.chatroom.common.chatai.properties.ChatGPTProperties;
import com.abin.chatroom.common.common.config.ThreadPoolConfig;
import com.abin.chatroom.common.common.constant.RedisKey;
import com.abin.chatroom.common.common.event.UserOfflineEvent;
import com.abin.chatroom.common.common.event.UserOnlineEvent;
import com.abin.chatroom.common.common.utils.RedisUtils;
import com.abin.chatroom.common.user.dao.UserDao;
import com.abin.chatroom.common.user.domain.dto.WSChannelExtraDTO;
import com.abin.chatroom.common.user.domain.entity.User;
import com.abin.chatroom.common.user.domain.enums.RoleEnum;
import com.abin.chatroom.common.user.domain.enums.WSBaseResp;
import com.abin.chatroom.common.user.domain.vo.request.ws.WSAuthorize;
import com.abin.chatroom.common.user.service.IRoleService;
import com.abin.chatroom.common.user.service.LoginService;
import com.abin.chatroom.common.user.service.WebSocketService;
import com.abin.chatroom.common.user.service.adapter.WSAdapter;
import com.abin.chatroom.common.user.service.cache.UserCache;
import com.abin.chatroom.common.websocket.NettyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {

    private static final Duration EXPIRE_TIME = Duration.ofHours(1);
    private static final Long MAX_MUM_SIZE = 10000L;

    public static final Cache<Integer, Channel> WAIT_LOGIN_MAP = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRE_TIME)
            .maximumSize(MAX_MUM_SIZE)
            .build();

    private static final ConcurrentHashMap<Channel, WSChannelExtraDTO> ONLINE_WS_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Channel>> ONLINE_UID_MAP = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<Channel, WSChannelExtraDTO> getOnlineMap() {
        return ONLINE_WS_MAP;
    }

    private static final String LOGIN_CODE = "loginCode";

    @Autowired
    private LoginService loginService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    @Qualifier(ThreadPoolConfig.WS_EXECUTOR)
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private UserCache userCache;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatGPTProperties chatGPTProperties;

    @Override
    public void handleLoginReq(Channel channel) {
        sendMsg(channel, WSAdapter.buildLoginUrlResp());
    }

    private Integer generateLoginCode(Channel channel) {
        int inc;
        do {
            inc = RedisUtils.integerInc(RedisKey.getKey(LOGIN_CODE), (int) EXPIRE_TIME.toMinutes(), TimeUnit.MINUTES);
        } while (WAIT_LOGIN_MAP.asMap().containsKey(inc));
        WAIT_LOGIN_MAP.put(inc, channel);
        return inc;
    }

    @Override
    public void connect(Channel channel) {
        ONLINE_WS_MAP.put(channel, new WSChannelExtraDTO());
    }

    @Override
    public void removed(Channel channel) {
        WSChannelExtraDTO wsChannelExtraDTO = ONLINE_WS_MAP.get(channel);
        Optional<Long> uidOptional = Optional.ofNullable(wsChannelExtraDTO)
                .map(WSChannelExtraDTO::getUid);
        boolean offlineAll = offline(channel, uidOptional);
        if (uidOptional.isPresent() && offlineAll) {
            User user = new User();
            user.setId(uidOptional.get());
            user.setLastOptTime(new Date());
            applicationEventPublisher.publishEvent(new UserOfflineEvent(this, user));
        }
    }

    @Override
    public void authorize(Channel channel, WSAuthorize wsAuthorize) {
        boolean verifySuccess = loginService.verify(wsAuthorize.getToken());
        if (verifySuccess) {
            User user = userDao.getById(loginService.getValidUid(wsAuthorize.getToken()));
            loginSuccess(channel, user, wsAuthorize.getToken());
        } else {
            sendMsg(channel, WSAdapter.buildInvalidateTokenResp());
        }
    }

    private void loginSuccess(Channel channel, User user, String token) {
        online(channel, user.getId());
        boolean hasPower = iRoleService.hasPower(user.getId(), RoleEnum.CHAT_MANAGER);
        sendMsg(channel, WSAdapter.buildLoginSuccessResp(user, token, hasPower));
        boolean online = userCache.isOnline(user.getId());
        if (!online) {
            user.setLastOptTime(new Date());
            user.refreshIp(NettyUtil.getAttr(channel, NettyUtil.IP));
            applicationEventPublisher.publishEvent(new UserOnlineEvent(this, user));
        }
    }

    private void online(Channel channel, Long uid) {
        getOrInitChannelExt(channel).setUid(uid);
        ONLINE_UID_MAP.putIfAbsent(uid, new CopyOnWriteArrayList<>());
        ONLINE_UID_MAP.get(uid).add(channel);
        NettyUtil.setAttr(channel, NettyUtil.UID, uid);
    }

    private boolean offline(Channel channel, Optional<Long> uidOptional) {
        ONLINE_WS_MAP.remove(channel);
        if (uidOptional.isPresent()) {
            CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(uidOptional.get());
            if (CollectionUtil.isNotEmpty(channels)) {
                channels.removeIf(ch -> Objects.equals(ch, channel));
            }
            return CollectionUtil.isEmpty(ONLINE_UID_MAP.get(uidOptional.get()));
        }
        return true;
    }

    @Override
    public Boolean scanLoginSuccess(Integer loginCode, Long uid) {
        Channel channel = WAIT_LOGIN_MAP.getIfPresent(loginCode);
        if (Objects.isNull(channel)) {
            return Boolean.FALSE;
        }
        User user = userDao.getById(uid);
        WAIT_LOGIN_MAP.invalidate(loginCode);
        String token = loginService.login(uid);
        loginSuccess(channel, user, token);
        return Boolean.TRUE;
    }

    @Override
    public Boolean scanSuccess(Integer loginCode) {
        Channel channel = WAIT_LOGIN_MAP.getIfPresent(loginCode);
        if (Objects.nonNull(channel)) {
            sendMsg(channel, WSAdapter.buildScanSuccessResp());
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private WSChannelExtraDTO getOrInitChannelExt(Channel channel) {
        WSChannelExtraDTO wsChannelExtraDTO =
                ONLINE_WS_MAP.getOrDefault(channel, new WSChannelExtraDTO());
        WSChannelExtraDTO old = ONLINE_WS_MAP.putIfAbsent(channel, wsChannelExtraDTO);
        return ObjectUtil.isNull(old) ? wsChannelExtraDTO : old;
    }

    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp, Long skipUid) {
        ONLINE_WS_MAP.forEach((channel, ext) -> {
            if (Objects.nonNull(skipUid) && Objects.equals(ext.getUid(), skipUid)) {
                return;
            }
            threadPoolTaskExecutor.execute(() -> sendMsg(channel, wsBaseResp));
        });
    }

    @Override
    public void sendToAllOnline(WSBaseResp<?> wsBaseResp) {
        sendToAllOnline(wsBaseResp, null);
    }

    @Override
    public void sendToUid(WSBaseResp<?> wsBaseResp, Long uid) {
        CopyOnWriteArrayList<Channel> channels = ONLINE_UID_MAP.get(uid);
        if (CollectionUtil.isEmpty(channels)) {
            log.info("用户：{}不在线", uid);
            return;
        }
        channels.forEach(channel -> {
            threadPoolTaskExecutor.execute(() -> sendMsg(channel, wsBaseResp));
        });
    }

    @Override
    public void sendAiReply(Channel channel, String reply, Long roomId) {
        Long aiUserId = chatGPTProperties.getAIUserId();
        ChatMessageReq answerReq = new ChatMessageReq();
        answerReq.setRoomId(roomId);
        answerReq.setMsgType(MessageTypeEnum.TEXT.getType());
        TextMsgReq textMsgReq = new TextMsgReq();
        textMsgReq.setContent(reply);
        answerReq.setBody(textMsgReq);
        chatService.sendMsg(answerReq, aiUserId);
    }

    private void sendMsg(Channel channel, WSBaseResp<?> wsBaseResp) {
        channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(wsBaseResp)));
    }
}