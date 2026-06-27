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

    public AgentRegistry(List<HrAgent<?, ?>> agentList) {
        this.agents = agentList.stream()
                .filter(HrAgent::isEnabled)
                .collect(Collectors.toMap(HrAgent::getAgentName, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public <T, R> R execute(String agentName, T context) {
        HrAgent<T, R> agent = (HrAgent<T, R>) agents.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found or disabled: " + agentName);
        }
        return agent.execute(context);
    }

    public boolean isAgentEnabled(String agentName) {
        return agents.containsKey(agentName);
    }
}
