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
                    你是一个专业的简历解析助手。请从以下简历文本中提取结构化信息，返回纯JSON格式（不要markdown代码块标记、不要任何额外说明）：

                    {
                      "name": "姓名",
                      "age": 年龄(数字,从出生日期或教育经历推算),
                      "email": "邮箱",
                      "phone": "电话",
                      "position": "应聘岗位/期望职位",
                      "yearsOfExperience": 工作年限(数字,整数),
                      "isFreshGraduate": true或false,
                      "graduationYear": 毕业年份(数字),
                      "educationLevel": "学历级别(枚举值)",
                      "school": "毕业院校/学校名称",
                      "major": "专业名称",
                      "techStack": ["技能1", "技能2"],
                      "workHistory": "工作经历摘要(包含公司名称、职位、时间段)",
                      "selfEvaluation": "自我评价/个人总结",
                      "projects": "项目经历(包含项目名称、技术栈、职责)"
                    }

                    字段说明：
                    - educationLevel 必须是以下枚举值之一：
                      BACHELOR = 本科、学士、本科在读
                      MASTER = 硕士、研究生、硕士研究生
                      PHD = 博士、博士后
                      ASSOCIATE = 大专、专科、高职
                      HIGH_SCHOOL = 高中、中专
                    - age：从出生年月或毕业年份推算当前年龄
                    - techStack：提取简历中出现的所有技术关键词（编程语言、框架、工具、平台等），尽量全面
                    - workHistory：每段工作经历的公司、职位、起止时间
                    - projects：主要项目经历

                    如果某个字段在简历中找不到对应的信息，使用null。

                    简历文本：
                    ---
                    """ + text;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是简历解析专家。只返回JSON，不要回复任何其他内容"),
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
            if (data.has("age") && !data.get("age").isNull()) c.setAge(data.get("age").asInt());
            if (data.has("email") && !data.get("email").isNull()) c.setEmail(data.get("email").asText());
            if (data.has("phone") && !data.get("phone").isNull()) c.setPhone(data.get("phone").asText());
            if (data.has("position") && !data.get("position").isNull()) c.setPosition(data.get("position").asText());
            if (data.has("yearsOfExperience") && !data.get("yearsOfExperience").isNull()) c.setYearsOfExperience(data.get("yearsOfExperience").asInt());
            if (data.has("isFreshGraduate") && !data.get("isFreshGraduate").isNull()) c.setIsFreshGraduate(data.get("isFreshGraduate").asBoolean());
            if (data.has("graduationYear") && !data.get("graduationYear").isNull()) c.setGraduationYear(data.get("graduationYear").asInt());
            if (data.has("educationLevel") && !data.get("educationLevel").isNull()) {
                try {
                    c.setEducationLevel(EducationLevel.valueOf(data.get("educationLevel").asText()));
                } catch (Exception e) {
                    log.warn("Failed to parse educationLevel from AI: {} (value: {})",
                            e.getMessage(), data.get("educationLevel").asText());
                }
            }
            if (data.has("school") && !data.get("school").isNull()) c.setSchool(data.get("school").asText());
            if (data.has("major") && !data.get("major").isNull()) c.setMajor(data.get("major").asText());
            if (data.has("techStack") && data.get("techStack").isArray()) {
                List<String> skills = new ArrayList<>();
                data.get("techStack").forEach(n -> skills.add(n.asText()));
                c.setTechStack(skills);
            }
            // 优先使用 projects，如果 workHistory 为空则用 projects 作为补充
            if (data.has("workHistory") && !data.get("workHistory").isNull()) {
                c.setWorkHistory(data.get("workHistory").asText());
            } else if (data.has("projects") && !data.get("projects").isNull()) {
                c.setWorkHistory(data.get("projects").asText());
            }
            if (data.has("selfEvaluation") && !data.get("selfEvaluation").isNull()) c.setSelfEvaluation(data.get("selfEvaluation").asText());

            log.info("AI parsed: {} position={} age={} edu={}", c.getName(), c.getPosition(), c.getAge(), c.getEducationLevel());
            return c;

        } catch (Exception e) {
            log.error("Real AI call failed, falling back to mock", e);
            return mockParse(text);
        }
    }

    private Candidate mockParse(String text) {
        Candidate c = new Candidate();
        c.setName(extractByPatternAny(text, new String[]{
                "(姓名[：:\\s]*([^\\n]{2,6}))",
                "(姓[\\s]*名[：:\\s]*([^\\n]{2,6}))",
                "(名字[：:\\s]*([^\\n]{2,6}))",
                "(Name[：:\\s]*([^\\n]+))"
        }, 2));
        c.setEmail(extractByPattern(text, "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", 1));
        c.setPhone(extractByPattern(text, "(1[3-9]\\d{9})", 1));
        c.setPosition(extractByPatternAny(text, new String[]{
                "(应聘岗位[：:\\s]*([^\\n]+))",
                "(期望职位[：:\\s]*([^\\n]+))",
                "(求职意向[：:\\s]*([^\\n]+))",
                "(意向岗位[：:\\s]*([^\\n]+))"
        }, 2));

        // 学校/毕业院校
        c.setSchool(extractByPatternAny(text, new String[]{
                "(毕业院校[：:\\s]*([^\\n]+))",
                "(学校[：:\\s]*([^\\n]+))",
                "(院校[：:\\s]*([^\\n]+))",
                "(毕业学校[：:\\s]*([^\\n]+))"
        }, 2));

        // 专业
        c.setMajor(extractByPatternAny(text, new String[]{
                "(专业[：:\\s]*([^\\n]+))",
                "(主修[：:\\s]*([^\\n]+))"
        }, 2));

        // 毕业年份
        String yearStr = extractByPatternAny(text, new String[]{
                "(毕业年份[：:\\s]*(\\d{4}))",
                "(毕业时间[：:\\s]*(\\d{4}))",
                "(毕业日期[：:\\s]*(\\d{4}))",
                "(20\\d{2})"
        }, 1);
        if (yearStr != null) {
            try { c.setGraduationYear(Integer.parseInt(yearStr)); } catch (Exception ignored) {}
        }

        // 工作年限
        String expStr = extractByPatternAny(text, new String[]{
                "(工作经验[：:\\s]*(\\d+))",
                "(工作年限[：:\\s]*(\\d+))",
                "(工作\\s*(\\d+)\\s*年)"
        }, 2);
        if (expStr != null) {
            try {
                c.setYearsOfExperience(Integer.parseInt(expStr));
                c.setIsFreshGraduate(c.getYearsOfExperience() == 0);
            } catch (Exception ignored) {}
        } else {
            c.setYearsOfExperience(0);
            c.setIsFreshGraduate(true);
        }

        // 年龄
        String ageStr = extractByPatternAny(text, new String[]{
                "(年龄[：:\\s]*(\\d+))",
                "(岁[，,\\s])"
        }, 2);
        if (ageStr != null) {
            try { c.setAge(Integer.parseInt(ageStr)); } catch (Exception ignored) {}
        }

        // 学历
        String edu = extractByPatternAny(text, new String[]{
                "(学历[：:\\s]*([^\\n]+))",
                "(学[\\s]*历[：:\\s]*([^\\n]+))",
                "(教育程度[：:\\s]*([^\\n]+))"
        }, 2);
        if (edu != null) {
            if (edu.contains("博") || edu.contains("博士") || edu.contains("博士后")) {
                c.setEducationLevel(EducationLevel.PHD);
            } else if (edu.contains("硕") || edu.contains("研究") || edu.contains("硕士")) {
                c.setEducationLevel(EducationLevel.MASTER);
            } else if (edu.contains("本") || edu.contains("学士") || edu.contains("本科在读")) {
                c.setEducationLevel(EducationLevel.BACHELOR);
            } else if (edu.contains("专") || edu.contains("专科") || edu.contains("高职")) {
                c.setEducationLevel(EducationLevel.ASSOCIATE);
            } else if (edu.contains("高") || edu.contains("中专")) {
                c.setEducationLevel(EducationLevel.HIGH_SCHOOL);
            }
        }

        // 技术栈 - 扩充关键词列表
        Set<String> skills = new LinkedHashSet<>();
        String[] keywords = {"Java","Spring","SpringBoot","SpringCloud","MyBatis","MySQL","Redis","MongoDB",
                "RabbitMQ","Kafka","Docker","Kubernetes","K8s","Vue","React","Angular","Node.js","TypeScript",
                "Python","Go","Linux","微服务","分布式","高并发","JVM","SQL","Elasticsearch",
                "Nginx","Jenkins","Git","Maven","Gradle","HTML","CSS","JavaScript","jQuery",
                "Oracle","PostgreSQL","SQLite","Flask","Django","TensorFlow","PyTorch",
                "机器学习","深度学习","NLP","AI","敏捷开发","Scrum","Bootstrap","Tailwind"};
        String lowerText = text.toLowerCase();
        for (String skill : keywords) {
            if (lowerText.contains(skill.toLowerCase())) skills.add(skill);
        }
        c.setTechStack(new ArrayList<>(skills));

        // workHistory - 尝试从文本提取
        c.setWorkHistory(extractSection(text, new String[]{"工作经历","工作经验","项目经历","项目经验"}));

        // selfEvaluation - 尝试从文本提取
        c.setSelfEvaluation(extractSection(text, new String[]{"自我评价","个人评价","个人总结","自我介绍","关于我"}));

        log.info("Mock parsed: {} position={}", c.getName(), c.getPosition());
        return c;
    }

    /**
     * 从文本中提取指定标签后的段落内容
     */
    private String extractSection(String text, String[] labels) {
        for (String label : labels) {
            // 匹配标签后的内容直到下一个空行或下一个标题标签
            Pattern p = Pattern.compile(
                    "(?:" + label + ")[：:\\s]*\\n?([\\s\\S]*?)(?=\\n\\s*\\n|$|\\n[\\u4e00-\\u9fa5]{2,6}[：:\\n])",
                    Pattern.MULTILINE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String section = m.group(1).trim();
                if (!section.isEmpty() && section.length() > 5) {
                    return section;
                }
            }
        }

        // 后备：截取简历末尾500字符
        if (text.length() > 500) {
            String tail = text.substring(text.length() - 500).trim();
            if (tail.length() > 50) return tail;
        }
        return null;
    }

    /**
     * 多个正则依次匹配，返回第一个命中的结果
     */
    private String extractByPatternAny(String text, String[] regexes, int group) {
        for (String regex : regexes) {
            String result = extractByPattern(text, regex, group);
            if (result != null) return result;
        }
        return null;
    }

    private String extractByPattern(String text, String regex, int group) {
        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(group).trim() : null;
    }
}
