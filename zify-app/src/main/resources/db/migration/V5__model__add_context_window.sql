-- model 表新增 context_window 列：供上下文预算计算（token）。
-- NULL 表示未配置，engine 按 zify.chat.context.default-window 兜底。
ALTER TABLE `model`
    ADD COLUMN `context_window` INT NULL COMMENT '模型上下文窗口大小（token），NULL 时用全局默认值';
