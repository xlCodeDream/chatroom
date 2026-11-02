package com.abin.chatroom.common.user.domain.vo.request.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Description: 修改个人介绍请求体
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2026-08-04
 */
@Data
@ApiModel("修改个人介绍请求体")
public class ModifySignatureReq {

    @Size(max = 100, message = "个人介绍长度不能超过100字符")
    @ApiModelProperty(value = "个人介绍", required = true)
    private String signature;
}