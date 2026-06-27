package com.wangmian.hr_manager_project.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangmian.hr_manager_project.agent.HrAgent;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.enums.EducationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "hr.agent.resume-parse.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParseAgent implements HrAgent<String, Candidate> {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseAgent.class);

    @Value("${hr.agent.resume-parse.ai.mock:true}")
    private boolean mockMode;

    @Value("${hr.agent.resume-parse.ai.api-key:}")
    private String apiKey;

    @Value("${hr.agent.resume-parse.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${hr.agent.resume-parse.ai.model:gpt-4o-mini}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public ResumeParseAgent() {
        this.restClient = RestClient.create();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String getAgentName() { return "resume-parse"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public Candidate execute(String extractedText) {
        if (mockMode || apiKey == null || apiKey.isEmpty()) {
            log.info("Mock mode: parsing with regex");
            return mockParse(extractedText);
        }
        return callRealAI(extractedText);
    }

    private Candidate callRealAI(String text) {
        try {
            String prompt = """
                    你是一个专业的简历解析助手。请从以下简历文本中提取结构化信息，返回纯JSON格式（不要markdown代码块标记）：

                    {
                      "name": "姓名",
                      "email": "邮箱",
                      "phone": "电话",
                      "position": "应聘岗位",
                      "yearsOfExperience": 工作年限(数字),
                      "isFreshGraduate": true/false,
                      "graduationYear": 毕业年份(数字),
                      "educationLevel": "BACHELOR/MASTER/PHD/ASSOCIATE/HIGH_SCHOOL",
                      "school": "毕业院校",
                      "major": "专业",
                      "techStack": ["技能1", "技能2"],
                      "workHistory": "工作经历摘要",
                      "selfEvaluation": "自我评价"
                    }

                    简历文本：
                    ---
                    """ + text;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个简历解析专家，只返回JSON"),
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

            // Strip potential markdown code block
            content = content.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)\\s*```$", "").trim();

            JsonNode data = mapper.readTree(content);
            Candidate c = new Candidate();
            if (data.has("name") && !data.get("name").isNull()) c.setName(data.get("name").asText());
            if (data.has("email") && !data.get("email").isNull()) c.setEmail(data.get("email").asText());
            if (data.has("phone") && !data.get("phone").isNull()) c.setPhone(data.get("phone").asText());
            if (data.has("position") && !data.get("position").isNull()) c.setPosition(data.get("position").asText());
            if (data.has("yearsOfExperience") && !data.get("yearsOfExperience").isNull()) c.setYearsOfExperience(data.get("yearsOfExperience").asInt());
            if (data.has("isFreshGraduate") && !data.get("isFreshGraduate").isNull()) c.setIsFreshGraduate(data.get("isFreshGraduate").asBoolean());
            if (data.has("graduationYear") && !data.get("graduationYear").isNull()) c.setGraduationYear(data.get("graduationYear").asInt());
            if (data.has("educationLevel") && !data.get("educationLevel").isNull()) {
                try { c.setEducationLevel(EducationLevel.valueOf(data.get("educationLevel").asText())); } catch (Exception ignored) {}
            }
            if (data.has("school") && !data.get("school").isNull()) c.setSchool(data.get("school").asText());
            if (data.has("major") && !data.get("major").isNull()) c.setMajor(data.get("major").asText());
            if (data.has("techStack") && data.get("techStack").isArray()) {
                List<String> skills = new ArrayList<>();
                data.get("techStack").forEach(n -> skills.add(n.asText()));
                c.setTechStack(skills);
            }
            if (data.has("workHistory") && !data.get("workHistory").isNull()) c.setWorkHistory(data.get("workHistory").asText());
            if (data.has("selfEvaluation") && !data.get("selfEvaluation").isNull()) c.setSelfEvaluation(data.get("selfEvaluation").asText());

            log.info("AI parsed: {} -> position={}", c.getName(), c.getPosition());
            return c;

        } catch (Exception e) {
            log.error("Real AI call failed, falling back to mock", e);
            return mockParse(text);
        }
    }

    private Candidate mockParse(String text) {
        Candidate c = new Candidate();
        c.setName(extractByPattern(text, "(姓名[：:\\\\s]*([^\\\\n]+))", 2));
        c.setEmail(extractByPattern(text, "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,})", 1));
        c.setPhone(extractByPattern(text, "(1[3-9]\\\\d{9})", 1));
        c.setPosition(extractByPattern(text, "(应聘岗位[：:\\\\s]*([^\\\\n]+))", 2));
        c.setSchool(extractByPattern(text, "(毕业院校[：:\\\\s]*([^\\\\n]+))", 2));
        c.setMajor(extractByPattern(text, "(专业[：:\\\\s]*([^\\\\n]+))", 2));
        String yearStr = extractByPattern(text, "(毕业年份[：:\\\\s]*(\\\\d{4}))", 1);
        if (yearStr == null) yearStr = extractByPattern(text, "(20\\\\d{2})", 1);
        if (yearStr != null) c.setGraduationYear(Integer.parseInt(yearStr));
        String expStr = extractByPattern(text, "(工作经验[：:\\\\s]*(\\\\d+))", 2);
        if (expStr != null) {
            c.setYearsOfExperience(Integer.parseInt(expStr));
            c.setIsFreshGraduate(c.getYearsOfExperience() == 0);
        } else {
            c.setYearsOfExperience(0);
            c.setIsFreshGraduate(true);
        }
        String edu = extractByPattern(text, "(学历[：:\\\\s]*([^\\\\n]+))", 2);
        if (edu != null) {
            if (edu.contains("博")) c.setEducationLevel(EducationLevel.PHD);
            else if (edu.contains("硕") || edu.contains("研究")) c.setEducationLevel(EducationLevel.MASTER);
            else if (edu.contains("本") || edu.contains("学士")) c.setEducationLevel(EducationLevel.BACHELOR);
            else if (edu.contains("专")) c.setEducationLevel(EducationLevel.ASSOCIATE);
            else c.setEducationLevel(EducationLevel.HIGH_SCHOOL);
        }
        Set<String> skills = new HashSet<>();
        String[] keywords = {"Java","Spring","SpringBoot","MyBatis","MySQL","Redis","MongoDB",
                "RabbitMQ","Kafka","Docker","Kubernetes","Vue","React","Python","Go","Linux",
                "微服务","分布式","高并发","JVM","SQL","Elasticsearch"};
        String lowerText = text.toLowerCase();
        for (String skill : keywords) {
            if (lowerText.contains(skill.toLowerCase())) skills.add(skill);
        }
        c.setTechStack(new ArrayList<>(skills));
        c.setSelfEvaluation("AI自动提取");
        c.setWorkHistory("AI自动提取");
        log.info("Mock parsed: {}", c.getName());
        return c;
    }

    private String extractByPattern(String text, String regex, int group) {
        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(group).trim() : null;
    }
}
