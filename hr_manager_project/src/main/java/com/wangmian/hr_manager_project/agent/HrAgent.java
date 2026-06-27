package com.wangmian.hr_manager_project.agent;

public interface HrAgent<T, R> {
    String getAgentName();
    boolean isEnabled();
    R execute(T context);
}
