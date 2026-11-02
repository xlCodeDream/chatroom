package com.abin.chatroom.common.common.event.listener;

import com.abin.chatroom.common.common.event.UserApplyEvent;
import com.abin.chatroom.common.user.dao.UserApplyDao;
import com.abin.chatroom.common.user.domain.entity.UserApply;
import com.abin.chatroom.common.user.domain.enums.ApplyTypeEnum;
import com.abin.chatroom.common.user.domain.vo.response.ws.WSFriendApply;
import com.abin.chatroom.common.user.service.WebSocketService;
import com.abin.chatroom.common.user.service.adapter.WSAdapter;
import com.abin.chatroom.common.user.service.impl.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * 好友申请监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class UserApplyListener {
    @Autowired
    private UserApplyDao userApplyDao;
    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private PushService pushService;

    @Async
    @TransactionalEventListener(classes = UserApplyEvent.class, fallbackExecution = true)
    public void notifyFriend(UserApplyEvent event) {
        UserApply userApply = event.getUserApply();
        // 加群申请不走好友申请通知，群主通过加群申请列表查看
        if (Objects.equals(userApply.getType(), ApplyTypeEnum.JOIN_GROUP.getCode())) {
            return;
        }
        Integer unReadCount = userApplyDao.getUnReadCount(userApply.getTargetId());
        pushService.sendPushMsg(WSAdapter.buildApplySend(new WSFriendApply(userApply.getUid(), unReadCount)), userApply.getTargetId());
    }

}
