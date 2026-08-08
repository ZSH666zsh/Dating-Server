package com.dating.im.service;

import com.dating.im.adaptor.ImProviderAdaptor;
import com.dating.im.model.event.ImEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回调解析服务。对应 im-service-design.md §4.1。
 * 遍历所有 adaptor 匹配 provider，解析 payload 为 ImEvent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final List<ImProviderAdaptor> adaptors;

    /** 解析回调 payload。返回 null 表示无 adaptor 能处理。 */
    public ImEvent parse(String provider, byte[] payload) {
        for (var adaptor : adaptors) {
            if (adaptor.supports(provider)) {
                return adaptor.parse(payload);
            }
        }
        log.warn("No adaptor found for provider: {}", provider);
        return null;
    }
}
