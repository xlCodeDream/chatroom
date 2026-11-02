package com.abin.chatroom.common.user.service.impl;

import com.abin.chatroom.common.common.utils.AssertUtil;
import com.abin.chatroom.common.user.domain.enums.OssSceneEnum;
import com.abin.chatroom.common.user.domain.vo.request.oss.UploadUrlReq;
import com.abin.chatroom.common.user.service.OssService;
import com.abin.chatroom.oss.MinIOTemplate;
import com.abin.chatroom.oss.domain.OssReq;
import com.abin.chatroom.oss.domain.OssResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-06-20
 */
@Service
public class OssServiceImpl implements OssService {
    @Autowired
    private MinIOTemplate minIOTemplate;

    @Override
    public OssResp getUploadUrl(Long uid, UploadUrlReq req) {
        OssSceneEnum sceneEnum = OssSceneEnum.of(req.getScene());
        AssertUtil.isNotEmpty(sceneEnum, "场景有误");
        OssReq ossReq = OssReq.builder()
                .fileName(req.getFileName())
                .filePath(sceneEnum.getPath())
                .uid(uid)
                .build();
        return minIOTemplate.getPreSignedObjectUrl(ossReq);
    }
}
