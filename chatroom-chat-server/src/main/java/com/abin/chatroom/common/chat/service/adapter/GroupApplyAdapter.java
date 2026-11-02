package com.abin.chatroom.common.chat.service.adapter;

import com.abin.chatroom.common.chat.domain.entity.RoomGroup;
import com.abin.chatroom.common.chat.domain.vo.response.GroupApplyResp;
import com.abin.chatroom.common.user.domain.entity.UserApply;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.abin.chatroom.common.user.domain.enums.ApplyReadStatusEnum.UNREAD;
import static com.abin.chatroom.common.user.domain.enums.ApplyStatusEnum.WAIT_APPROVAL;
import static com.abin.chatroom.common.user.domain.enums.ApplyTypeEnum.JOIN_GROUP;

/**
 * Description: 加群申请适配器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-07-22
 */
public class GroupApplyAdapter {

    /**
     * 构建加群申请记录
     *
     * @param uid       申请人uid
     * @param targetId  接收人uid(群主)
     * @param roomId    群房间号
     * @param msg       申请信息
     */
    public static UserApply buildGroupApply(Long uid, Long targetId, Long roomId, String msg) {
        UserApply userApply = new UserApply();
        userApply.setUid(uid);
        userApply.setTargetId(targetId);
        userApply.setTargetRoomId(roomId);
        userApply.setMsg(msg);
        userApply.setType(JOIN_GROUP.getCode());
        userApply.setStatus(WAIT_APPROVAL.getCode());
        userApply.setReadStatus(UNREAD.getCode());
        return userApply;
    }

    /**
     * 构建加群申请列表响应
     */
    public static List<GroupApplyResp> buildGroupApplyRespList(List<UserApply> records, Map<Long, RoomGroup> roomGroupMap) {
        return records.stream().map(userApply -> {
            GroupApplyResp resp = new GroupApplyResp();
            resp.setApplyId(userApply.getId());
            resp.setUid(userApply.getUid());
            resp.setRoomId(userApply.getTargetRoomId());
            resp.setMsg(userApply.getMsg());
            resp.setStatus(userApply.getStatus());
            RoomGroup roomGroup = roomGroupMap.get(userApply.getTargetRoomId());
            if (roomGroup != null) {
                resp.setGroupName(roomGroup.getName());
                resp.setGroupAvatar(roomGroup.getAvatar());
            }
            return resp;
        }).collect(Collectors.toList());
    }
}
