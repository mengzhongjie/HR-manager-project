package com.wangmian.hr_manager_project.agent.impl;

import com.wangmian.hr_manager_project.agent.HrAgent;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.enums.EducationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "hr.agent.resume-parse.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeParseAgent implements HrAgent<String, Candidate> {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseAgent.class);

    @Value("${hr.agent.ai.mock:true}")
    private boolean mockMode;

    @Override
    public String getAgentName() { return "resume-parse"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public Candidate execute(String extractedText) {
        if (mockMode) return mockParse(extractedText);
        return callRealAI(extractedText);
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

    private Candidate callRealAI(String text) {
        log.warn("Real AI not available, fallback to mock");
        return mockParse(text);
    }
}
