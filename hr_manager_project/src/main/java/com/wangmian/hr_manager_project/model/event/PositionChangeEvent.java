package com.wangmian.hr_manager_project.model.event;

import java.time.LocalDateTime;

public class PositionChangeEvent {

    private final String action;
    private final String positionId;
    private final String positionName;
    private final LocalDateTime timestamp;

    public PositionChangeEvent(String action, String positionId, String positionName) {
        this.action = action;
        this.positionId = positionId;
        this.positionName = positionName;
        this.timestamp = LocalDateTime.now();
    }

    public String getAction() { return action; }
    public String getPositionId() { return positionId; }
    public String getPositionName() { return positionName; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
