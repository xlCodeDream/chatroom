package com.abin.chatroom.common.user.domain.vo.response.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description: 用户信息变更的推送类
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-08-06
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSUserChange {
    @ApiModelProperty("用户id")
    private Long uid;
    @ApiModelProperty("变更后的头像")
    private String avatar;
    @ApiModelProperty("变更后的昵称")
    private String name;
    @ApiModelProperty("变更后的个人介绍")
    private String signature;
}
