package com.dating.im.adaptor;

import com.dating.im.model.event.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenIM 回调解析适配器。
 * 将 OpenIM webhook JSON 解析为归一化 ImEvent。
 *
 * <p>实现说明：im-service 回调处理，Phase 3 实现。
 * OpenIM 回调命令类型通过 callbackCommand 字段识别。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenImAdaptor implements ImProviderAdaptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String provider) {
        return "openim".equals(provider);
    }

    @Override
    public ImEvent parse(byte[] rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String cmd = root.path("callbackCommand").asText("");

            return switch (cmd) {
                case "callbackBeforeSendSingleMsgCommand" -> parseBeforeSend(root);
                case "callbackAfterSendSingleMsgCommand" -> parseAfterSend(root);
                case "callbackUserOnlineCommand" -> parseOnline(root);
                case "callbackUserOfflineCommand" -> parseOffline(root);
                default -> new UnknownEvent("openim", cmd);
            };
        } catch (Exception e) {
            log.warn("OpenIM callback parse failed", e);
            return new UnknownEvent("openim", "parse_error");
        }
    }

    private MessageBeforeSendEvent parseBeforeSend(JsonNode root) {
        return new MessageBeforeSendEvent(
                "openim",
                root.path("serverMsgID").asText(),
                root.path("sendID").asLong(),
                root.path("recvID").asLong(),
                root.path("content").asText(),
                root.path("contentType").asText()
        );
    }

    private MessageSentEvent parseAfterSend(JsonNode root) {
        return new MessageSentEvent(
                "openim",
                root.path("serverMsgID").asText(),
                root.path("sendID").asLong(),
                root.path("recvID").asLong(),
                root.path("content").asText(),
                root.path("contentType").asText(),
                root.path("sendTime").asLong(),
                extractImageUrl(root)
        );
    }

    private UserOnlineEvent parseOnline(JsonNode root) {
        return new UserOnlineEvent("openim",
                root.path("userID").asLong(),
                root.path("platformID").asInt(),
                root.path("onlineTime").asLong());
    }

    private UserOfflineEvent parseOffline(JsonNode root) {
        return new UserOfflineEvent("openim",
                root.path("userID").asLong(),
                root.path("offlineTime").asLong());
    }

    /** 从 PictureElem 提取图片 URL（缩略图→大图→原图兜底）。 */
    private String extractImageUrl(JsonNode root) {
        try {
            JsonNode pictureElem = root.path("pictureElem");
            if (pictureElem.isMissingNode()) return "";
            String url = pictureElem.path("snapshotPicture").path("url").asText("");
            if (url.isEmpty()) url = pictureElem.path("sourcePicture").path("url").asText("");
            if (url.isEmpty()) url = pictureElem.path("bigPicture").path("url").asText("");
            return url;
        } catch (Exception e) {
            return "";
        }
    }
}
