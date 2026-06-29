package com.wangmian.hr_manager_project.agent;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentRegistry {
    private final Map<String, HrAgent<?, ?>> agents;

    /**
     * 构造注册表，从 Spring 容器注入的所有 Agent 中筛选出已启用的进行注册
     *
     * @param agentList Agent 实例列表（由 Spring 自动注入）
     */
    public AgentRegistry(List<HrAgent<?, ?>> agentList) {
        this.agents = agentList.stream()
                .filter(HrAgent::isEnabled)
                .collect(Collectors.toMap(HrAgent::getAgentName, Function.identity()));
    }

    /**
     * 根据 Agent 名称执行对应的业务逻辑
     *
     * @param agentName Agent 名称
     * @param context   输入上下文
     * @param <T>       输入类型
     * @param <R>       返回类型
     * @return 执行结果
     * @throws IllegalArgumentException 如果指定名称的 Agent 不存在或未启用
     */
    @SuppressWarnings("unchecked")
    public <T, R> R execute(String agentName, T context) {
        HrAgent<T, R> agent = (HrAgent<T, R>) agents.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found or disabled: " + agentName);
        }
        return agent.execute(context);
    }

    /**
     * 判断指定名称的 Agent 是否已启用并注册
     *
     * @param agentName Agent 名称
     * @return true 表示已启用，false 表示未启用或不存在
     */
    public boolean isAgentEnabled(String agentName) {
        return agents.containsKey(agentName);
    }
}
