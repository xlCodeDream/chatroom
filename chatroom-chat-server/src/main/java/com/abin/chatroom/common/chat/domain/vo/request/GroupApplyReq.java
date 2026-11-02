package com.abin.chatroom.common.chat.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Description: 申请加群请求
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-07-22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupApplyReq {

    @NotNull
    @ApiModelProperty("群房间号")
    private Long roomId;

    @NotBlank
    @ApiModelProperty("申请信息")
    private String msg;

}
