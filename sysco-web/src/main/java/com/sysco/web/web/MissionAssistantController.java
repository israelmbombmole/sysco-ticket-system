package com.sysco.web.web;

import com.sysco.web.service.MissionAssistantService;
import com.sysco.web.web.dto.MissionAssistantDtos.MissionAssistantRequest;
import com.sysco.web.web.dto.MissionAssistantDtos.MissionAssistantResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/missions/api/assistant")
@RequiredArgsConstructor
public class MissionAssistantController {

    private final MissionAssistantService missionAssistantService;

    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public MissionAssistantResponse chat(@RequestBody(required = false) MissionAssistantRequest req, Locale locale) {
        String msg = req == null || req.message() == null ? "" : req.message();
        String ctx = req == null || req.context() == null ? "" : req.context();
        return missionAssistantService.reply(msg, ctx, locale);
    }
}
