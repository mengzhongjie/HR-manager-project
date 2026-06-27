package com.wangmian.hr_manager_project.config;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Candidate.AiQualification;
import com.wangmian.hr_manager_project.model.document.Candidate.StatusHistoryEntry;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.enums.*;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CandidateRepository candidateRepository;
    private final InterviewRecordRepository interviewRepository;

    public DataInitializer(CandidateRepository candidateRepository,
                           InterviewRecordRepository interviewRepository) {
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
    }

    @Override
    public void run(String... args) {
        if (candidateRepository.count() > 0) {
            log.info("Data already initialized, skipping");
            return;
        }

        log.info("Initializing seed data...");

        // Position 1: Java后端工程师
        Candidate c1 = createCandidate("张三", "zhangsan@email.com", "13800138001",
                "Java后端工程师", 5, false, 2020, EducationLevel.BACHELOR,
                "华中科技大学", "计算机科学与技术",
                List.of("Java", "Spring", "SpringBoot", "MyBatis", "MySQL", "Redis"),
                CandidateStatus.IN_INTERVIEW, 2, 82, "INTERVIEW_INVITED");

        Candidate c2 = createCandidate("李四", "lisi@email.com", "13900139002",
                "Java后端工程师", 3, false, 2022, EducationLevel.MASTER,
                "武汉大学", "软件工程",
                List.of("Java", "Spring", "SpringBoot", "MySQL", "Kafka"),
                CandidateStatus.NEW, 0, 75, "PENDING_REVIEW");

        Candidate c3 = createCandidate("王五", "wangwu@email.com", "13700137003",
                "Java后端工程师", 0, true, 2025, EducationLevel.BACHELOR,
                "华中师范大学", "计算机科学",
                List.of("Java", "Spring", "MySQL"),
                CandidateStatus.NEW, 0, 60, "PENDING_REVIEW");

        Candidate c4 = createCandidate("赵六", "zhaoliu@email.com", "13600136004",
                "Java后端工程师", 8, false, 2017, EducationLevel.BACHELOR,
                "湖南大学", "信息管理",
                List.of("Java", "Spring", "SpringBoot", "MySQL", "Redis", "MongoDB", "Docker"),
                CandidateStatus.PENDING_ARCHIVE, 0, 88, "INTERVIEW_INVITED");

        c1 = candidateRepository.save(c1);
        c2 = candidateRepository.save(c2);
        c3 = candidateRepository.save(c3);
        c4 = candidateRepository.save(c4);

        // Add interviews for c1 (Round 1 passed, Round 2 passed)
        InterviewRecord iv1 = new InterviewRecord();
        iv1.setCandidateId(c1.getId());
        iv1.setCandidateName(c1.getName());
        iv1.setCandidatePosition(c1.getPosition());
        iv1.setRound(InterviewRound.ROUND_1);
        iv1.setInterviewerName("王面试官");
        iv1.setInterviewDate(LocalDate.now().minusDays(7));
        iv1.setResult(InterviewResult.PASSED);
        iv1.setScore(85);
        iv1.setFeedback("技术基础扎实，项目经验丰富");
        interviewRepository.save(iv1);

        InterviewRecord iv2 = new InterviewRecord();
        iv2.setCandidateId(c1.getId());
        iv2.setCandidateName(c1.getName());
        iv2.setCandidatePosition(c1.getPosition());
        iv2.setRound(InterviewRound.ROUND_2);
        iv2.setInterviewerName("李面试官");
        iv2.setInterviewDate(LocalDate.now().minusDays(2));
        iv2.setResult(InterviewResult.PASSED);
        iv2.setScore(80);
        iv2.setFeedback("业务理解深入，沟通表达流畅");
        interviewRepository.save(iv2);

        // Position 2: 前端工程师
        Candidate c5 = createCandidate("孙七", "sunqi@email.com", "13500135005",
                "前端工程师", 4, false, 2021, EducationLevel.BACHELOR,
                "南京大学", "软件工程",
                List.of("Vue", "React", "TypeScript", "JavaScript", "CSS"),
                CandidateStatus.NEW, 0, 78, "PENDING_REVIEW");

        Candidate c6 = createCandidate("周八", "zhouba@email.com", "13400134006",
                "前端工程师", 2, false, 2023, EducationLevel.MASTER,
                "东南大学", "计算机技术",
                List.of("Vue", "JavaScript", "HTML", "ElementUI"),
                CandidateStatus.INTERVIEW_INVITED, 0, 68, "PENDING_REVIEW");

        c5 = candidateRepository.save(c5);
        c6 = candidateRepository.save(c6);

        // Interview invitation for c6
        StatusHistoryEntry inviteEntry = new StatusHistoryEntry();
        inviteEntry.setEventId(UUID.randomUUID().toString());
        inviteEntry.setFromStatus(CandidateStatus.NEW);
        inviteEntry.setToStatus(CandidateStatus.INTERVIEW_INVITED);
        inviteEntry.setTimestamp(LocalDateTime.now());
        inviteEntry.setActor("HR");
        inviteEntry.setReason("AI推荐，HR确认邀约面试");
        c6.getStatusHistory().add(inviteEntry);
        candidateRepository.save(c6);

        // Position 3: 产品经理
        Candidate c7 = createCandidate("吴九", "wujiu@email.com", "13300133007",
                "产品经理", 6, false, 2019, EducationLevel.MASTER,
                "浙江大学", "管理学",
                List.of("Axure", "Figma", "PRD", "数据分析", "用户调研"),
                CandidateStatus.WAITING_OFFER, 3, 90, "INTERVIEW_INVITED");

        c7 = candidateRepository.save(c7);

        // Add interviews for c7 (3 rounds all passed)
        for (int r = 1; r <= 3; r++) {
            InterviewRecord iv = new InterviewRecord();
            iv.setCandidateId(c7.getId());
            iv.setCandidateName(c7.getName());
            iv.setCandidatePosition(c7.getPosition());
            iv.setRound(r == 1 ? InterviewRound.ROUND_1 : r == 2 ? InterviewRound.ROUND_2 : InterviewRound.ROUND_3);
            iv.setInterviewerName("产品总监");
            iv.setInterviewDate(LocalDate.now().minusDays(10 - r * 3));
            iv.setResult(InterviewResult.PASSED);
            iv.setScore(85 + r);
            iv.setFeedback("需求分析能力优秀" + (r == 3 ? "，整体素质符合要求" : ""));
            interviewRepository.save(iv);
        }

        log.info("Seed data initialized: {} candidates, {} positions",
                candidateRepository.count(), candidateRepository.findDistinctPositionBy().size());
    }

    private Candidate createCandidate(String name, String email, String phone,
                                      String position, int exp, boolean fresh,
                                      int gradYear, EducationLevel edu,
                                      String school, String major,
                                      List<String> techStack,
                                      CandidateStatus status, int interviewRound,
                                      int aiScore, String aiRecommendation) {
        Candidate c = new Candidate();
        c.setName(name);
        c.setEmail(email);
        c.setPhone(phone);
        c.setPosition(position);
        c.setYearsOfExperience(exp);
        c.setIsFreshGraduate(fresh);
        c.setGraduationYear(gradYear);
        c.setEducationLevel(edu);
        c.setSchool(school);
        c.setMajor(major);
        c.setTechStack(techStack);
        c.setStatus(status);
        c.setInterviewRound(interviewRound);
        c.setWorkHistory(name + "拥有" + exp + "年" + position + "工作经验");
        c.setSelfEvaluation("熟悉" + String.join("、", techStack) + "等技术");

        AiQualification ai = new AiQualification();
        ai.setScore(aiScore);
        ai.setRecommendation(aiRecommendation);
        c.setAiQualification(ai);

        StatusHistoryEntry entry = new StatusHistoryEntry();
        entry.setEventId(UUID.randomUUID().toString());
        entry.setFromStatus(null);
        entry.setToStatus(status);
        entry.setTimestamp(LocalDateTime.now());
        entry.setActor("SYSTEM");
        entry.setReason("种子数据初始化");
        c.getStatusHistory().add(entry);

        return c;
    }
}
