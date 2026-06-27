package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.service.PositionSseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/hr/positions")
public class PositionSseController {

    private final PositionSseService sseService;

    public PositionSseController(PositionSseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/events")
    public SseEmitter subscribe() {
        return sseService.subscribe();
    }
}
