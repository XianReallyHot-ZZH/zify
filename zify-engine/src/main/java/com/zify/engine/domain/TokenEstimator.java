package com.zify.engine.domain;

import org.springframework.stereotype.Component;

/**
 * 近似 token 估算器（字符启发式，留 ~10% buffer）。
 * <p>
 * 不引入各家 tokenizer；精确化留二期。规则：CJK 每字约 1 token，拉丁约 4 字符/token。
 * <p>
 * 放 engine（chat 依赖 engine 复用），供 ContextManager 预算估算与 chat 单条消息上限估算共用。
 */
@Component
public class TokenEstimator {

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        // CJK 约 1 token/字；拉丁约 4 字符/token（向上取整）
        return cjk + (other + 3) / 4;
    }

    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK Unified Ideographs
                || (c >= 0x3400 && c <= 0x4DBF) // CJK Extension A
                || (c >= 0x3000 && c <= 0x30FF) // CJK symbols + Hiragana/Katakana
                || (c >= 0xFF00 && c <= 0xFFEF); // Fullwidth forms
    }
}
