package com.sysco.web.web;

import com.sysco.web.service.CreateTicketAssistantService;
import com.sysco.web.service.CreateTicketHandlingRoutingService;
import com.sysco.web.web.dto.CreateTicketAssistantDtos.ChatRequest;
import com.sysco.web.web.dto.CreateTicketAssistantDtos.ChatResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/create-ticket/api/assistant")
@RequiredArgsConstructor
public class CreateTicketAssistantController {

    private final CreateTicketAssistantService assistant;
    private final CreateTicketHandlingRoutingService handlingRouting;

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_TICKET') or hasAuthority('CREATE_TICKET_READ') or hasAuthority('CREATE_TICKET_WRITE')")
    public ChatResponse chat(@RequestBody(required = false) ChatRequest req, HttpSession session, Locale locale) {
        String msg = req == null ? "" : req.message();
        return assistant.handleMessage(session, msg, locale);
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_TICKET') or hasAuthority('CREATE_TICKET_READ') or hasAuthority('CREATE_TICKET_WRITE')")
    public ResponseEntity<Void> reset(HttpSession session) {
        assistant.reset(session);
        return ResponseEntity.noContent().build();
    }

    /**
     * Keyword-based DGDA handling suggestion for the create-ticket form (debounced client-side).
     * Empty JSON object when no confident match.
     */
    @PostMapping(
            value = "/suggest-handling",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_TICKET') or hasAuthority('CREATE_TICKET_READ') or hasAuthority('CREATE_TICKET_WRITE')")
    public Map<String, String> suggestHandling(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> b = body == null ? Map.of() : body;
        return handlingRouting
                .suggest(
                        b.getOrDefault("issuePresetCode", ""),
                        b.getOrDefault("summary", ""),
                        b.getOrDefault("description", ""))
                .map(
                        h -> {
                            Map<String, String> out = new LinkedHashMap<>();
                            out.put("handlingSousDirectionId", String.valueOf(h.sousDirectionId()));
                            out.put("handlingDirectionId", String.valueOf(h.directionId()));
                            return out;
                        })
                .orElseGet(Map::of);
    }
}
