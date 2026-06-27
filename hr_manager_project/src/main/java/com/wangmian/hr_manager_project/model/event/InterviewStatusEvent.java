package com.wangmian.hr_manager_project.model.event;

import java.time.LocalDateTime;

public class InterviewStatusEvent {

    private final String action;         // ACCEPTED / REJECTED
    private final String candidateId;
    private final String candidateName;
    private final String position;
    private final LocalDateTime timestamp;

    public InterviewStatusEvent(String action, String candidateId, String candidateName, String position) {
        this.action = action;
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.position = position;
        this.timestamp = LocalDateTime.now();
    }

    public String getAction() { return action; }
    public String getCandidateId() { return candidateId; }
    public String getCandidateName() { return candidateName; }
    public String getPosition() { return position; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
