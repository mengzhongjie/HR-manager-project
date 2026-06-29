package com.wangmian.hr_manager_project.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangmian.hr_manager_project.agent.HrAgent;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Candidate.AiQualification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "hr.agent.candidate-qualify.enabled", havingValue = "true", matchIfMissing = true)
public class CandidateQualifyAgent implements HrAgent<Candidate, AiQualification> {

    private static final Logger log = LoggerFactory.getLogger(CandidateQualifyAgent.class);

    @Value("${hr.agent.candidate-qualify.ai.mock:true}")
    private boolean mockMode;

    @Value("${hr.agent.candidate-qualify.ai.api-key:}")
    private String apiKey;

    @Value("${hr.agent.candidate-qualify.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${hr.agent.candidate-qualify.ai.model:gpt-4o-mini}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public CandidateQualifyAgent() {
        this.restClient = RestClient.create();
        this.mapper = new ObjectMapper();
    }

    /**
     * 获取 Agent 名称
     *
     * @return Agent 名称 "candidate-qualify"
     */
    @Override
    public String getAgentName() { return "candidate-qualify"; }

    /**
     * 判断 Agent 是否启用
     *
     * @return 始终返回 true（启用状态由 @ConditionalOnProperty 控制）
     */
    @Override
    public boolean isEnabled() { return true; }

    /**
     * 执行候选人资质评估，根据候选人信息给出评分和推荐意见
     * <p>根据配置选择模拟模式（规则评分）或调用真实 AI 进行评估</p>
     *
     * @param candidate 候选人信息
     * @return 资质评估结果，包含评分和推荐操作
     */
    @Override
    public AiQualification execute(Candidate candidate) {
        if (mockMode || apiKey == null || apiKey.isEmpty()) {
            return mockQualify(candidate);
        }
        return callRealAI(candidate);
    }

    private AiQualification callRealAI(Candidate candidate) {
        try {
            String info = String.format(
                    "姓名：%s，岗位：%s，工作经验：%d年，应届：%s，学历：%s，技术栈：%s，自我评价：%s",
                    candidate.getName(), candidate.getPosition(),
                    candidate.getYearsOfExperience(),
                    Boolean.TRUE.equals(candidate.getIsFreshGraduate()) ? "是" : "否",
                    candidate.getEducationLevel(), candidate.getTechStack(),
                    candidate.getSelfEvaluation()
            );

            String prompt = """
                    你是一个HR资质评估助手。请根据候选人信息给出资质评分和推荐意见，只返回JSON：

                    {
                      "score": 0-100,
                      "recommendation": "INTERVIEW_INVITED / PENDING_REVIEW / PENDING_ARCHIVE"
                    }

                    评估标准：
                    - INTERVIEW_INVITED (>=80分)：经验丰富、技术栈匹配
                    - PENDING_REVIEW (60-79分)：基本符合，需HR进一步判断
                    - PENDING_ARCHIVE (<60分)：经验或技能不匹配

                    候选人信息：""" + info;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是HR资质评估专家，只返回JSON"),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.1);

            String response = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            content = content.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)\\s*```$", "").trim();

            JsonNode data = mapper.readTree(content);
            AiQualification result = new AiQualification();
            result.setScore(data.has("score") ? data.get("score").asInt() : 60);
            result.setRecommendation(data.has("recommendation") ? data.get("recommendation").asText() : "PENDING_REVIEW");

            log.info("AI qualify {}: score={}", candidate.getName(), result.getScore());
            return result;

        } catch (Exception e) {
            log.error("Real AI qualify call failed, fallback to mock", e);
            return mockQualify(candidate);
        }
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
}
