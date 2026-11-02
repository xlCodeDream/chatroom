package com.abin.chatroom.common.chat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.abin.chatroom.common.chat.dao.ContactDao;
import com.abin.chatroom.common.chat.dao.GroupMemberDao;
import com.abin.chatroom.common.chat.dao.MessageDao;
import com.abin.chatroom.common.chat.domain.dto.RoomBaseInfo;
import com.abin.chatroom.common.chat.domain.entity.*;
import com.abin.chatroom.common.chat.domain.enums.GroupRoleAPPEnum;
import com.abin.chatroom.common.chat.domain.enums.GroupRoleEnum;
import com.abin.chatroom.common.chat.domain.enums.HotFlagEnum;
import com.abin.chatroom.common.chat.domain.enums.RoomTypeEnum;
import com.abin.chatroom.common.chat.domain.vo.request.ChatMessageMemberReq;
import com.abin.chatroom.common.chat.domain.vo.request.GroupAddReq;
import com.abin.chatroom.common.chat.domain.vo.request.member.MemberAddReq;
import com.abin.chatroom.common.chat.domain.vo.request.member.MemberDelReq;
import com.abin.chatroom.common.chat.domain.vo.request.member.MemberReq;
import com.abin.chatroom.common.chat.domain.vo.response.ChatMemberListResp;
import com.abin.chatroom.common.chat.domain.vo.response.ChatRoomResp;
import com.abin.chatroom.common.chat.domain.vo.response.MemberResp;
import com.abin.chatroom.common.chat.service.ChatService;
import com.abin.chatroom.common.chat.service.RoomAppService;
import com.abin.chatroom.common.chat.service.RoomService;
import com.abin.chatroom.common.chat.service.adapter.ChatAdapter;
import com.abin.chatroom.common.chat.service.adapter.MemberAdapter;
import com.abin.chatroom.common.chat.service.adapter.RoomAdapter;
import com.abin.chatroom.common.chat.service.cache.*;
import com.abin.chatroom.common.chat.service.strategy.msg.AbstractMsgHandler;
import com.abin.chatroom.common.chat.service.strategy.msg.MsgHandlerFactory;
import com.abin.chatroom.common.common.annotation.RedissonLock;
import com.abin.chatroom.common.common.domain.vo.request.CursorPageBaseReq;
import com.abin.chatroom.common.common.domain.vo.response.CursorPageBaseResp;
import com.abin.chatroom.common.common.event.GroupMemberAddEvent;
import com.abin.chatroom.common.common.exception.GroupErrorEnum;
import com.abin.chatroom.common.common.utils.AssertUtil;
import com.abin.chatroom.common.user.dao.UserDao;
import com.abin.chatroom.common.user.domain.entity.User;
import com.abin.chatroom.common.user.domain.enums.RoleEnum;
import com.abin.chatroom.common.user.domain.enums.WSBaseResp;
import com.abin.chatroom.common.user.domain.vo.response.ws.ChatMemberResp;
import com.abin.chatroom.common.user.domain.vo.response.ws.WSMemberChange;
import com.abin.chatroom.common.user.service.IRoleService;
import com.abin.chatroom.common.user.service.cache.UserCache;
import com.abin.chatroom.common.user.service.cache.UserInfoCache;
import com.abin.chatroom.common.user.service.impl.PushService;
import com.abin.chatroom.common.chat.domain.vo.request.GroupApplyReq;
import com.abin.chatroom.common.chat.domain.vo.request.GroupApproveReq;
import com.abin.chatroom.common.chat.domain.vo.response.GroupApplyResp;
import com.abin.chatroom.common.chat.service.adapter.GroupApplyAdapter;
import com.abin.chatroom.common.common.domain.vo.request.PageBaseReq;
import com.abin.chatroom.common.common.domain.vo.response.PageBaseResp;
import com.abin.chatroom.common.common.event.UserApplyEvent;
import com.abin.chatroom.common.user.dao.UserApplyDao;
import com.abin.chatroom.common.user.domain.entity.UserApply;
import com.abin.chatroom.common.user.domain.enums.ApplyStatusEnum;
import com.abin.chatroom.common.user.domain.enums.ApplyTypeEnum;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-07-22
 */
@Service
public class RoomAppServiceImpl implements RoomAppService {

    @Autowired
    private ContactDao contactDao;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private RoomGroupCache roomGroupCache;
    @Autowired
    private RoomFriendCache roomFriendCache;
    @Autowired
    private UserInfoCache userInfoCache;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private HotRoomCache hotRoomCache;
    @Autowired
    private UserCache userCache;
    @Autowired
    private GroupMemberDao groupMemberDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ChatService chatService;
    @Autowired
    private IRoleService iRoleService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private UserApplyDao userApplyDao;
    @Autowired
    private RoomService roomService;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private PushService pushService;

    @Override
    public CursorPageBaseResp<ChatRoomResp> getContactPage(CursorPageBaseReq request, Long uid) {
        // 查出用户要展示的会话列表
        CursorPageBaseResp<Long> page;
        if (Objects.nonNull(uid)) {
            Double hotEnd = getCursorOrNull(request.getCursor());
            Double hotStart = null;
            // 用户基础会话
            CursorPageBaseResp<Contact> contactPage = contactDao.getContactPage(uid, request);
            List<Long> baseRoomIds = contactPage.getList().stream().map(Contact::getRoomId).collect(Collectors.toList());
            if (!contactPage.getIsLast()) {
                hotStart = getCursorOrNull(contactPage.getCursor());
            }
            // 热门房间
            Set<ZSetOperations.TypedTuple<String>> typedTuples = hotRoomCache.getRoomRange(hotStart, hotEnd);
            List<Long> hotRoomIds = typedTuples.stream().map(ZSetOperations.TypedTuple::getValue).filter(Objects::nonNull).map(Long::parseLong).collect(Collectors.toList());
            baseRoomIds.addAll(hotRoomIds);
            // 基础会话和热门房间合并
            page = CursorPageBaseResp.init(contactPage, baseRoomIds);
        } else {// 用户未登录，只查全局房间
            CursorPageBaseResp<Pair<Long, Double>> roomCursorPage = hotRoomCache.getRoomCursorPage(request);
            List<Long> roomIds = roomCursorPage.getList().stream().map(Pair::getKey).collect(Collectors.toList());
            page = CursorPageBaseResp.init(roomCursorPage, roomIds);
        }
        if (CollectionUtil.isEmpty(page.getList())) {
            return CursorPageBaseResp.empty();
        }
        // 最后组装会话信息（名称，头像，未读数等）
        List<ChatRoomResp> result = buildContactResp(uid, page.getList());
        return CursorPageBaseResp.init(page, result);
    }

    @Override
    public ChatRoomResp getContactDetail(Long uid, Long roomId) {
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(room, "房间号有误");
        return buildContactResp(uid, Collections.singletonList(roomId)).get(0);
    }

    @Override
    public ChatRoomResp getContactDetailByFriend(Long uid, Long friendUid) {
        RoomFriend friendRoom = roomService.getFriendRoom(uid, friendUid);
        AssertUtil.isNotEmpty(friendRoom, "他不是您的好友");
        return buildContactResp(uid, Collections.singletonList(friendRoom.getRoomId())).get(0);
    }

    @Override
    public MemberResp getGroupDetail(Long uid, long roomId) {
        RoomGroup roomGroup = roomGroupCache.get(roomId);
        Room room = roomCache.get(roomId);
        AssertUtil.isNotEmpty(roomGroup, "roomId有误");
        Long onlineNum;
        if (isHotGroup(room)) {// 热点群从redis取人数
            onlineNum = userCache.getOnlineNum();
        } else {
            List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            onlineNum = userDao.getOnlineCount(memberUidList).longValue();
        }
        GroupRoleAPPEnum groupRole = getGroupRole(uid, roomGroup, room);
        return MemberResp.builder()
                .avatar(roomGroup.getAvatar())
                .roomId(roomId)
                .groupName(roomGroup.getName())
                .onlineNum(onlineNum)
                .role(groupRole.getType())
                .build();
    }

    @Override
    public CursorPageBaseResp<ChatMemberResp> getMemberPage(MemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        List<Long> memberUidList;
        if (isHotGroup(room)) {// 全员群展示所有用户
            memberUidList = null;
        } else {// 只展示房间内的群成员
            RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
            memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
        }
        return chatService.getMemberPage(memberUidList, request);
    }

    @Override
    @Cacheable(cacheNames = "member", key = "'memberList.'+#request.roomId")
    public List<ChatMemberListResp> getMemberList(ChatMessageMemberReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        if (isHotGroup(room)) {// 全员群展示所有用户100名
            List<User> memberList = userDao.getMemberList();
            return MemberAdapter.buildMemberList(memberList);
        } else {
            RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
            List<Long> memberUidList = groupMemberDao.getMemberUidList(roomGroup.getId());
            Map<Long, User> batch = userInfoCache.getBatch(memberUidList);
            return MemberAdapter.buildMemberList(batch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delMember(Long uid, MemberDelReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, GroupErrorEnum.USER_NOT_IN_GROUP);
        // 1. 判断被移除的人是否是群主或者管理员  （群主不可以被移除，管理员只能被群主移除）
        Long removedUid = request.getUid();
        // 1.1 群主 非法操作
        AssertUtil.isFalse(groupMemberDao.isLord(roomGroup.getId(), removedUid), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        // 1.2 管理员 判断是否是群主操作
        if (groupMemberDao.isManager(roomGroup.getId(), removedUid)) {
            Boolean isLord = groupMemberDao.isLord(roomGroup.getId(), uid);
            AssertUtil.isTrue(isLord, GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        }
        // 1.3 普通成员 判断是否有权限操作
        AssertUtil.isTrue(hasPower(self), GroupErrorEnum.NOT_ALLOWED_FOR_REMOVE);
        GroupMember member = groupMemberDao.getMember(roomGroup.getId(), removedUid);
        AssertUtil.isNotEmpty(member, "用户已经移除");
        groupMemberDao.removeById(member.getId());
        // 发送移除事件告知群成员
        List<Long> memberUidList = groupMemberCache.getMemberUidList(roomGroup.getRoomId());
        WSBaseResp<WSMemberChange> ws = MemberAdapter.buildMemberRemoveWS(roomGroup.getRoomId(), member.getUid());
        pushService.sendPushMsg(ws, memberUidList);
        groupMemberCache.evictMemberUidList(room.getId());
    }


    @Override
    @RedissonLock(key = "#request.roomId")
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long uid, MemberAddReq request) {
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        AssertUtil.isFalse(isHotGroup(room), "全员群无需邀请好友");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isNotEmpty(self, "您不是群成员");
        List<Long> memberBatch = groupMemberDao.getMemberBatch(roomGroup.getId(), request.getUidList());
        Set<Long> existUid = new HashSet<>(memberBatch);
        List<Long> waitAddUidList = request.getUidList().stream().filter(a -> !existUid.contains(a)).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(waitAddUidList)) {
            return;
        }
        List<GroupMember> groupMembers = MemberAdapter.buildMemberAdd(roomGroup.getId(), waitAddUidList);
        groupMemberDao.saveBatch(groupMembers);
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, groupMembers, uid));
    }

    @Override
    @Transactional
    public Long addGroup(Long uid, GroupAddReq request) {
        RoomGroup roomGroup = roomService.createGroupRoom(uid);
        // 批量保存群成员
        List<GroupMember> groupMembers = RoomAdapter.buildGroupMemberBatch(request.getUidList(), roomGroup.getId());
        groupMemberDao.saveBatch(groupMembers);
        // 发送邀请加群消息==》触发每个人的会话
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, groupMembers, uid));
        return roomGroup.getRoomId();
    }

    @Override
    @RedissonLock(key = "#uid")
    public void groupApply(Long uid, GroupApplyReq request) {
        // 校验群存在且非全员群
        Room room = roomCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(room, "房间号有误");
        AssertUtil.isFalse(isHotGroup(room), "全员群无需申请");
        RoomGroup roomGroup = roomGroupCache.get(request.getRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        // 校验申请人不在群中
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        AssertUtil.isEmpty(self, "您已经在群中");
        // 查重
        UserApply applying = userApplyDao.getGroupApproving(uid, request.getRoomId());
        if (Objects.nonNull(applying)) {
            return;
        }
        // 查群主uid
        Long lordUid = groupMemberDao.getLordUid(roomGroup.getId());
        AssertUtil.isNotEmpty(lordUid, "群主不存在");
        // 入库
        UserApply insert = GroupApplyAdapter.buildGroupApply(uid, lordUid, request.getRoomId(), request.getMsg());
        userApplyDao.save(insert);
        // 推送给群主
        applicationEventPublisher.publishEvent(new UserApplyEvent(this, insert));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RedissonLock(key = "#uid")
    public void approveGroupApply(Long uid, GroupApproveReq request) {
        UserApply userApply = userApplyDao.getById(request.getApplyId());
        AssertUtil.isNotEmpty(userApply, "不存在申请记录");
        AssertUtil.equal(userApply.getType(), ApplyTypeEnum.JOIN_GROUP.getCode(), "不是加群申请");
        AssertUtil.equal(userApply.getStatus(), ApplyStatusEnum.WAIT_APPROVAL.getCode(), "申请已处理");
        // 校验审批权限：群主/管理员/系统管理员
        RoomGroup roomGroup = roomGroupCache.get(userApply.getTargetRoomId());
        AssertUtil.isNotEmpty(roomGroup, "房间号有误");
        GroupMember self = groupMemberDao.getMember(roomGroup.getId(), uid);
        boolean canApprove = self != null ? hasPower(self) : iRoleService.hasPower(uid, RoleEnum.ADMIN);
        AssertUtil.isTrue(canApprove, "无权限操作");
        // 同意申请
        userApplyDao.agree(request.getApplyId());
        // 加入群成员
        GroupMember member = MemberAdapter.buildMemberAdd(roomGroup.getId(), Collections.singletonList(userApply.getUid())).get(0);
        groupMemberDao.save(member);
        // 触发加群事件（欢迎消息+成员变动推送）
        applicationEventPublisher.publishEvent(new GroupMemberAddEvent(this, roomGroup, Collections.singletonList(member), uid));
    }

    @Override
    public PageBaseResp<GroupApplyResp> pageGroupApply(Long uid, PageBaseReq request) {
        IPage<UserApply> userApplyIPage = userApplyDao.groupApplyPage(uid, request.plusPage());
        // 设为已读
        List<Long> applyIds = userApplyIPage.getRecords().stream().map(UserApply::getId).collect(Collectors.toList());
        if (!CollectionUtil.isEmpty(applyIds)) {
            userApplyDao.readApples(uid, applyIds);
        }
        // 批量查群信息
        Set<Long> roomIds = userApplyIPage.getRecords().stream().map(UserApply::getTargetRoomId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RoomGroup> roomGroupMap = new HashMap<>();
        for (Long roomId : roomIds) {
            RoomGroup roomGroup = roomGroupCache.get(roomId);
            if (roomGroup != null) {
                roomGroupMap.put(roomId, roomGroup);
            }
        }
        return PageBaseResp.init(userApplyIPage, GroupApplyAdapter.buildGroupApplyRespList(userApplyIPage.getRecords(), roomGroupMap));
    }

    private boolean hasPower(GroupMember self) {
        return Objects.equals(self.getRole(), GroupRoleEnum.LEADER.getType())
                || Objects.equals(self.getRole(), GroupRoleEnum.MANAGER.getType())
                || iRoleService.hasPower(self.getUid(), RoleEnum.ADMIN);

    }

    private GroupRoleAPPEnum getGroupRole(Long uid, RoomGroup roomGroup, Room room) {
        GroupMember member = Objects.isNull(uid) ? null : groupMemberDao.getMember(roomGroup.getId(), uid);
        if (Objects.nonNull(member)) {
            return GroupRoleAPPEnum.of(member.getRole());
        } else if (isHotGroup(room)) {
            return GroupRoleAPPEnum.MEMBER;
        } else {
            return GroupRoleAPPEnum.REMOVE;
        }
    }

    private boolean isHotGroup(Room room) {
        return HotFlagEnum.YES.getType().equals(room.getHotFlag());
    }

    private List<Contact> buildContact(List<Pair<Long, Double>> list, Long uid) {
        List<Long> roomIds = list.stream().map(Pair::getKey).collect(Collectors.toList());
        Map<Long, Room> batch = roomCache.getBatch(roomIds);
        Map<Long, Contact> contactMap = new HashMap<>();
        if (Objects.nonNull(uid)) {
            List<Contact> byRoomIds = contactDao.getByRoomIds(roomIds, uid);
            contactMap = byRoomIds.stream().collect(Collectors.toMap(Contact::getRoomId, Function.identity()));
        }
        Map<Long, Contact> finalContactMap = contactMap;
        return list.stream().map(pair -> {
            Long roomId = pair.getKey();
            Contact contact = finalContactMap.get(roomId);
            if (Objects.isNull(contact)) {
                contact = new Contact();
                contact.setRoomId(pair.getKey());
                Room room = batch.get(roomId);
                contact.setLastMsgId(room.getLastMsgId());
            }
            contact.setActiveTime(new Date(pair.getValue().longValue()));
            return contact;
        }).collect(Collectors.toList());
    }

    private Double getCursorOrNull(String cursor) {
        return Optional.ofNullable(cursor).map(Double::parseDouble).orElse(null);
    }

    @NotNull
    private List<ChatRoomResp> buildContactResp(Long uid, List<Long> roomIds) {
        // 表情和头像
        Map<Long, RoomBaseInfo> roomBaseInfoMap = getRoomBaseInfoMap(roomIds, uid);
        // 最后一条消息
        List<Long> msgIds = roomBaseInfoMap.values().stream().map(RoomBaseInfo::getLastMsgId).collect(Collectors.toList());
        List<Message> messages = CollectionUtil.isEmpty(msgIds) ? new ArrayList<>() : messageDao.listByIds(msgIds);
        Map<Long, Message> msgMap = messages.stream().collect(Collectors.toMap(Message::getId, Function.identity()));
        Map<Long, User> lastMsgUidMap = userInfoCache.getBatch(messages.stream().map(Message::getFromUid).collect(Collectors.toList()));
        // 消息未读数
        Map<Long, Integer> unReadCountMap = getUnReadCountMap(uid, roomIds);
        return roomBaseInfoMap.values().stream().map(room -> {
                    ChatRoomResp resp = new ChatRoomResp();
                    RoomBaseInfo roomBaseInfo = roomBaseInfoMap.get(room.getRoomId());
                    resp.setAvatar(roomBaseInfo.getAvatar());
                    resp.setRoomId(room.getRoomId());
                    resp.setActiveTime(room.getActiveTime());
                    resp.setHot_Flag(roomBaseInfo.getHotFlag());
                    resp.setType(roomBaseInfo.getType());
                    resp.setName(roomBaseInfo.getName());
                    Message message = msgMap.get(room.getLastMsgId());
                    if (Objects.nonNull(message)) {
                        AbstractMsgHandler strategyNoNull = MsgHandlerFactory.getStrategyNoNull(message.getType());
                        resp.setText(lastMsgUidMap.get(message.getFromUid()).getName() + ":" + strategyNoNull.showContactMsg(message));
                    }
                    resp.setUnreadCount(unReadCountMap.getOrDefault(room.getRoomId(), 0));
                    return resp;
                }).sorted(Comparator.comparing(ChatRoomResp::getActiveTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取未读数
     */
    private Map<Long, Integer> getUnReadCountMap(Long uid, List<Long> roomIds) {
        if (Objects.isNull(uid)) {
            return new HashMap<>();
        }
        List<Contact> contacts = contactDao.getByRoomIds(roomIds, uid);
        return contacts.parallelStream()
                .map(contact -> Pair.of(contact.getRoomId(), messageDao.getUnReadCount(contact.getRoomId(), contact.getReadTime())))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private Map<Long, User> getFriendRoomMap(List<Long> roomIds, Long uid) {
        if (CollectionUtil.isEmpty(roomIds)) {
            return new HashMap<>();
        }
        Map<Long, RoomFriend> roomFriendMap = roomFriendCache.getBatch(roomIds);
        Set<Long> friendUidSet = ChatAdapter.getFriendUidSet(roomFriendMap.values(), uid);
        Map<Long, User> userBatch = userInfoCache.getBatch(new ArrayList<>(friendUidSet));
        return roomFriendMap.values()
                .stream()
                .collect(Collectors.toMap(RoomFriend::getRoomId, roomFriend -> {
                    Long friendUid = ChatAdapter.getFriendUid(roomFriend, uid);
                    return userBatch.get(friendUid);
                }));
    }

    private Map<Long, RoomBaseInfo> getRoomBaseInfoMap(List<Long> roomIds, Long uid) {
        Map<Long, Room> roomMap = roomCache.getBatch(roomIds);
        // 房间根据好友和群组类型分组
        Map<Integer, List<Long>> groupRoomIdMap = roomMap.values().stream().collect(Collectors.groupingBy(Room::getType,
                Collectors.mapping(Room::getId, Collectors.toList())));
        // 获取群组信息
        List<Long> groupRoomId = groupRoomIdMap.get(RoomTypeEnum.GROUP.getType());
        Map<Long, RoomGroup> roomInfoBatch = roomGroupCache.getBatch(groupRoomId);
        // 获取好友信息
        List<Long> friendRoomId = groupRoomIdMap.get(RoomTypeEnum.FRIEND.getType());
        Map<Long, User> friendRoomMap = getFriendRoomMap(friendRoomId, uid);

        return roomMap.values().stream().map(room -> {
            RoomBaseInfo roomBaseInfo = new RoomBaseInfo();
            roomBaseInfo.setRoomId(room.getId());
            roomBaseInfo.setType(room.getType());
            roomBaseInfo.setHotFlag(room.getHotFlag());
            roomBaseInfo.setLastMsgId(room.getLastMsgId());
            roomBaseInfo.setActiveTime(room.getActiveTime());
            if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.GROUP) {
                RoomGroup roomGroup = roomInfoBatch.get(room.getId());
                roomBaseInfo.setName(roomGroup.getName());
                roomBaseInfo.setAvatar(roomGroup.getAvatar());
            } else if (RoomTypeEnum.of(room.getType()) == RoomTypeEnum.FRIEND) {
                User user = friendRoomMap.get(room.getId());
                roomBaseInfo.setName(user.getName());
                roomBaseInfo.setAvatar(user.getAvatar());
            }
            return roomBaseInfo;
        }).collect(Collectors.toMap(RoomBaseInfo::getRoomId, Function.identity()));
    }

    private void fillRoomActive(Long uid, Map<Long, Room> roomMap) {
    }

}
