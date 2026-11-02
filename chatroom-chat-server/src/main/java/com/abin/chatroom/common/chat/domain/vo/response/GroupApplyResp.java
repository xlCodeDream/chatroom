package com.abin.chatroom.common.chat.domain.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description: 加群申请列表响应
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-07-22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupApplyResp {

    @ApiModelProperty("申请id")
    private Long applyId;

    @ApiModelProperty("申请人uid")
    private Long uid;

    @ApiModelProperty("群房间号")
    private Long roomId;

    @ApiModelProperty("申请信息")
    private String msg;

    @ApiModelProperty("申请状态 1待审批 2同意")
    private Integer status;

    @ApiModelProperty("群名称")
    private String groupName;

    @ApiModelProperty("群头像")
    private String groupAvatar;

}
