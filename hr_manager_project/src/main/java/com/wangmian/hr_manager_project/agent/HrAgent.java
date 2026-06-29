package com.wangmian.hr_manager_project.agent;

public interface HrAgent<T, R> {
    /**
     * 获取 Agent 的唯一标识名称
     *
     * @return Agent 名称
     */
    String getAgentName();

    /**
     * 判断 Agent 是否启用
     *
     * @return true 表示启用，false 表示禁用
     */
    boolean isEnabled();

    /**
     * 执行 Agent 的核心业务逻辑
     *
     * @param context 输入上下文，类型由泛型 T 决定
     * @return 执行结果，类型由泛型 R 决定
     */
    R execute(T context);
}
