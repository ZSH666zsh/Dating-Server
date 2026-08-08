package com.dating.im.adaptor;

import com.dating.im.model.event.ImEvent;

/**
 * IM 引擎回调解析适配器接口。对应 im-service-design.md §3 provider 抽象。
 * 平台当前用 OpenIM，未来换腾讯 IM 只加一个实现类。
 */
public interface ImProviderAdaptor {
    /** 此 adaptor 是否能处理该 provider？ */
    boolean supports(String provider);
    /** 将原始回调 JSON 解析为归一化 ImEvent。 */
    ImEvent parse(byte[] rawPayload);
}
