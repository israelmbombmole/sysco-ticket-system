package com.sysco.web.web.dto;

import java.util.Map;

public final class CreateTicketAssistantDtos {

    private CreateTicketAssistantDtos() {}

    public record ChatRequest(String message) {}

    /**
     * @param reply Assistant message shown in the chat UI
     * @param prefill Optional field ids → values for the HTML form (summary, description, issuePresetCode, priority,
     *     handlingSousDirectionId, handlingDirectionId for DGDA traitement — server-filled when applicable)
     * @param conversationComplete When true, dropdown routing fields should be completed manually and the user can submit to
     *     obtain the ticket reference
     * @param assistantMode {@code scripted} or {@code openai}
     */
    public record ChatResponse(
            String reply, Map<String, String> prefill, boolean conversationComplete, String assistantMode) {}
}
