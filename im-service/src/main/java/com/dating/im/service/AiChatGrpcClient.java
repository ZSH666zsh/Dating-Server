package com.dating.im.service;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ai-chat gRPC 客户端。调 ChatAgent.chat 生成 DH 自动回复。
 *
 * <p>实现说明：ai-chat 为 Python 项目（LangChain/LangGraph），待部署后接入。
 * 当前返回 null 表示 ai-chat 不可用，不阻断主流程。
 */
@Slf4j
@Component
public class AiChatGrpcClient {

    // TODO: 等 ai-chat (Python) 部署后，添加 @GrpcClient("ai-chat") ChatAgentGrpc stub

    /**
     * 调 ai-chat 生成回复。
     * @param threadId 会话 ID（{fromUserId}:{toUserId}）
     * @param message  用户消息
     * @return AI 回复文本，null 表示不可用
     */
    public String chat(String threadId, String message) {
        log.debug("AiChatGrpcClient.chat(stub): threadId={} msgLen={}", threadId, message.length());
        // TODO: 替换为真实 gRPC 调用
        return null;
    }

    /**
     * 调 VisionAgent.understand 理解图片。
     * @param imageUrl 图片 URL
     * @return 图片描述，null 表示不可用
     */
    public String understandImage(String imageUrl) {
        log.debug("AiChatGrpcClient.understandImage(stub): url={}", imageUrl);
        // TODO: 替换为真实 gRPC 调用
        return null;
    }
}
