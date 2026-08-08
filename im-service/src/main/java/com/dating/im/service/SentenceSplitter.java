package com.dating.im.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 回复分句器。对应 im-service-design.md §5.3。
 * 按句末标点切句，ASCII 小数点 3.14 有守卫不误切。
 */
@Component
public class SentenceSplitter {

    private static final String SENTENCE_END = "。！？.!?…";
    private static final String NUMBER_DOT = ".";

    /**
     * 将文本按句子切分，合并短句不超过 maxMessages 段。
     */
    public List<String> split(String text, int maxMessages) {
        if (text == null || text.isEmpty()) return List.of();

        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char[] chars = text.toCharArray();
        int len = chars.length;

        for (int i = 0; i < len; i++) {
            char c = chars[i];
            current.append(c);

            // 句末标点且后面有空格或结尾则切
            if (SENTENCE_END.indexOf(c) >= 0) {
                // 小数点守卫：x.y 且前面是数字，不切
                if (c == '.' && i > 0 && i + 1 < len
                        && Character.isDigit(chars[i - 1])
                        && Character.isDigit(chars[i + 1])) {
                    continue;
                }
                sentences.add(current.toString().trim());
                current = new StringBuilder();
            }
        }

        // 剩余部分
        if (!current.isEmpty()) {
            sentences.add(current.toString().trim());
        }

        // 如果句数 <= maxMessages 直接返回
        if (sentences.size() <= maxMessages) {
            return sentences;
        }

        // 超过 maxMessages：取前 maxMessages-1 句，剩余合并到最后一句
        List<String> result = new ArrayList<>(sentences.subList(0, maxMessages - 1));
        StringBuilder last = new StringBuilder();
        for (int i = maxMessages - 1; i < sentences.size(); i++) {
            last.append(sentences.get(i));
        }
        result.add(last.toString());
        return result;
    }
}
