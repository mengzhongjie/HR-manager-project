package com.wangmian.hr_manager_project.agent.impl;

import com.wangmian.hr_manager_project.agent.HrAgent;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Candidate.AiQualification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hr.agent.candidate-qualify.enabled", havingValue = "true", matchIfMissing = true)
public class CandidateQualifyAgent implements HrAgent<Candidate, AiQualification> {

    private static final Logger log = LoggerFactory.getLogger(CandidateQualifyAgent.class);

    @Value("${hr.agent.ai.mock:true}")
    private boolean mockMode;

    @Override
    public String getAgentName() { return "candidate-qualify"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public AiQualification execute(Candidate candidate) {
        if (mockMode) return mockQualify(candidate);
        return callRealAI(candidate);
    }

    private AiQualification mockQualify(Candidate c) {
        AiQualification result = new AiQualification();
        int score = 60;
        if (c.getYearsOfExperience() != null) score += Math.min(c.getYearsOfExperience() * 5, 20);
        if (c.getEducationLevel() != null) {
            switch (c.getEducationLevel()) {
                case PHD: score += 15; break;
                case MASTER: score += 10; break;
                case BACHELOR: score += 5; break;
                default: break;
            }
        }
        if (c.getTechStack() != null) {
            score += Math.min(c.getTechStack().size() * 3, 15);
            if (c.getTechStack().size() >= 5) score += 5;
        }
        if (Boolean.TRUE.equals(c.getIsFreshGraduate())) score -= 5;
        score = Math.min(Math.max(score, 0), 100);
        String recommendation;
        if (score >= 80) recommendation = "INTERVIEW_INVITED";
        else if (score >= 60) recommendation = "PENDING_REVIEW";
        else recommendation = "PENDING_ARCHIVE";
        result.setScore(score);
        result.setRecommendation(recommendation);
        return result;
    }

    private AiQualification callRealAI(Candidate candidate) {
        log.warn("Real AI not available, fallback to mock");
        return mockQualify(candidate);
    }
}
