package com.abin.chatroom.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * Description: 审批加群请求
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-07-22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupApproveReq {

    @NotNull
    @ApiModelProperty("申请id")
    private Long applyId;

}
