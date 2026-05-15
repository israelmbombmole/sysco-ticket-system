package com.sysco.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mission assistant chat must return JSON for authenticated users present in {@code users}
 * (see {@link com.sysco.web.security.ForcePasswordChangeInterceptor}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionAssistantApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "superadmin", roles = {"ADMIN"})
    void missionAssistantChat_returnsJson_localMode() throws Exception {
        mockMvc.perform(
                        post("/app/missions/api/assistant/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"message\":\"Aide-moi à rédiger l'objet de la mission pour l'ordre.\",\"context\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.source").value("local"))
                .andExpect(jsonPath("$.reply").isString())
                .andExpect(jsonPath("$.reply", containsString("Objet")));
    }
}
