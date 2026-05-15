package com.sysco.web.service;

import com.sysco.web.repo.AutomatedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically runs {@link AutomatedJobProcessingService} so scheduled tasks actually notify
 * assignees and link activity to tickets.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutomatedJobPlannerProcessor {

    private final AutomatedJobRepository jobs;
    private final AutomatedJobProcessingService processing;

    @Scheduled(fixedDelayString = "${sysco.scheduler.jobs-poll-ms:60000}")
    public void tick() {
        jobs.findByActive(1).forEach(j -> {
            try {
                processing.handleOne(j.getId());
            } catch (Exception e) {
                log.warn("Automated job {} poll failed: {}", j.getId(), e.toString());
            }
        });
    }
}
