package com.sysco.web.web.dto;

import java.util.Collections;
import java.util.Map;

/** Request / response for the missions module assistant chat API. */
public final class MissionAssistantDtos {

    private MissionAssistantDtos() {}

    public record MissionAssistantRequest(String message, String context) {}

    /**
     * @param reply assistant message shown in the thread
     * @param source {@code openai} or {@code local}
     * @param prefill optional suggested values keyed by mission form field names (applied client-side to empty fields)
     */
    public record MissionAssistantResponse(String reply, String source, Map<String, String> prefill) {

        public MissionAssistantResponse(String reply, String source) {
            this(reply, source, Collections.emptyMap());
        }
    }
}
