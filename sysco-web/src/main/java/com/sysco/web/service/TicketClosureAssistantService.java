package com.sysco.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketEvent;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketEventRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.util.UiLabels;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Builds a concise closure briefing for senior reviewers (OpenAI when configured, else deterministic fallback). */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketClosureAssistantService {

    private static final int MAX_CONTEXT_CHARS = 9000;
    private static final int MAX_AI_CHARS = 3500;

    private final TicketEventRepository ticketEvents;
    private final AutomatedJobRepository jobs;
    private final TicketAssignmentRepository ticketAssignments;
    private final UserAccountRepository users;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final MessageSource messageSource;
    private final UiLabels uiLabels;

    @Value("${sysco.assistant.openai-api-key:}")
    private String openAiApiKey;

    @Value("${sysco.assistant.openai-base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${sysco.assistant.openai-model:gpt-4o-mini}")
    private String openAiModel;

    /** Briefing text plus whether it was generated locally (no / failed external model). */
    public record ClosureBriefing(String text, boolean localFallback) {}

    public String briefingForClosureReview(Ticket ticket, Locale locale) {
        return closureBriefingForTicket(ticket, locale).text();
    }

    public ClosureBriefing closureBriefingForTicket(Ticket ticket, Locale locale) {
        if (ticket == null || ticket.getId() == null) {
            return new ClosureBriefing("", false);
        }
        Locale loc = locale != null ? locale : Locale.FRENCH;
        Map<Long, String> userNames =
                users.findAll().stream()
                        .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        String context = buildContextBlock(ticket, userNames, loc);
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return new ClosureBriefing(fallbackBriefing(ticket, userNames, loc), true);
        }
        try {
            String ai = callOpenAiBriefing(context, loc);
            if (ai != null && !ai.isBlank()) {
                return new ClosureBriefing(truncate(ai.trim(), MAX_AI_CHARS), false);
            }
        } catch (Exception ex) {
            log.warn("closure briefing OpenAI failed: {}", ex.getMessage());
        }
        return new ClosureBriefing(fallbackBriefing(ticket, userNames, loc), true);
    }

    private String fallbackBriefing(Ticket ticket, Map<Long, String> userNames, Locale locale) {
        List<AutomatedJob> ticketJobs =
                jobs.findByTicketId(ticket.getId()).stream()
                        .sorted(Comparator.comparing(AutomatedJob::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
        String synopsis = buildLocalSynopsis(ticket, ticketJobs, userNames, locale);
        StringBuilder tasks = new StringBuilder();
        for (AutomatedJob j : ticketJobs) {
            String assignee =
                    j.getAssigneeUserId() == null ? "—" : userNames.getOrDefault(j.getAssigneeUserId(), "?");
            tasks.append("- ")
                    .append(nullSafe(j.getJobTitle()))
                    .append(" — ")
                    .append(uiLabels.taskStatus(jobStatusCode(j), locale))
                    .append(" — ")
                    .append(assignee)
                    .append('\n');
        }
        if (tasks.isEmpty()) {
            tasks.append(messageSource.getMessage("ticketMgmt.closure.ai.fallbackNoTasks", null, locale)).append('\n');
        }
        String title = nullSafe(ticket.getTitle());
        String description = truncate(nullSafe(ticket.getDescription()), 1200);
        String statusLine = uiLabels.ticketStatus(ticket.getStatus(), locale);
        return synopsis
                + "\n\n"
                + messageSource.getMessage("ticketMgmt.closure.ai.fallbackTitleLabel", null, locale)
                + " "
                + title
                + "\n"
                + messageSource.getMessage("ticketMgmt.closure.ai.fallbackStatusLabel", null, locale)
                + " "
                + statusLine
                + "\n"
                + messageSource.getMessage("ticketMgmt.closure.ai.fallbackAssigneesLabel", null, locale)
                + " "
                + participantNamesLine(ticket, userNames)
                + "\n\n"
                + messageSource.getMessage("ticketMgmt.closure.ai.fallbackDescLabel", null, locale)
                + "\n"
                + description
                + "\n\n"
                + messageSource.getMessage("ticketMgmt.closure.ai.fallbackTasksLabel", null, locale)
                + "\n"
                + tasks;
    }

    private String buildLocalSynopsis(
            Ticket ticket, List<AutomatedJob> ticketJobs, Map<Long, String> userNames, Locale locale) {
        String ref = nullSafe(ticket.getTicketNumber());
        String title = truncate(nullSafe(ticket.getTitle()), 200);
        String statusLbl = uiLabels.ticketStatus(ticket.getStatus(), locale);
        String agents = participantNamesLine(ticket, userNames);
        int total = ticketJobs.size();
        if (total == 0) {
            return messageSource.getMessage(
                    "ticketMgmt.closure.ai.localSynopsis.noTasks", new Object[] {ref, title, statusLbl, agents}, locale);
        }
        long closed =
                ticketJobs.stream().filter(j -> "CLOSED".equalsIgnoreCase(jobStatusCode(j))).count();
        long open = total - closed;
        if (open <= 0) {
            return messageSource.getMessage(
                    "ticketMgmt.closure.ai.localSynopsis.allTasksClosed",
                    new Object[] {ref, title, statusLbl, total, agents},
                    locale);
        }
        return messageSource.getMessage(
                "ticketMgmt.closure.ai.localSynopsis.someOpen",
                new Object[] {ref, title, statusLbl, open, total, agents},
                locale);
    }

    private String participantNamesLine(Ticket ticket, Map<Long, String> userNames) {
        if (ticket == null || userNames == null) {
            return "—";
        }
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        if (ticket.getAssignedTo() != null) {
            ids.add(ticket.getAssignedTo());
        }
        ticketAssignments.findByTicketId(ticket.getId()).forEach(a -> ids.add(a.getUserId()));
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Long uid : ids) {
            if (uid == null) {
                continue;
            }
            String u = userNames.getOrDefault(uid, "");
            if (!u.isBlank()) {
                names.add(u);
            }
        }
        return names.isEmpty() ? "—" : String.join(", ", names);
    }

    private static String jobStatusCode(AutomatedJob j) {
        String s = nullSafe(j.getStatus());
        if (!s.isBlank()) {
            return s;
        }
        return (j.getActive() != null && j.getActive() == 1) ? "OPEN" : "CLOSED";
    }

    private String buildContextBlock(Ticket ticket, Map<Long, String> userNames, Locale locale) {
        boolean french = locale != null && Locale.FRENCH.getLanguage().equals(locale.getLanguage());
        StringBuilder sb = new StringBuilder();
        if (french) {
            sb.append("Référence ticket: ")
                    .append(nullSafe(ticket.getTicketNumber()))
                    .append("\nIdentifiant interne: ")
                    .append(ticket.getId())
                    .append("\nTitre: ")
                    .append(nullSafe(ticket.getTitle()))
                    .append("\nStatut (libellé): ")
                    .append(uiLabels.ticketStatus(ticket.getStatus(), locale))
                    .append(" — code: ")
                    .append(nullSafe(ticket.getStatus()))
                    .append("\nPriorité: ")
                    .append(nullSafe(ticket.getPriority()))
                    .append("\nType: ")
                    .append(nullSafe(ticket.getTicketType()))
                    .append("\nAssignation principale (id utilisateur): ")
                    .append(ticket.getAssignedTo())
                    .append("\nAgents (identifiants): ")
                    .append(participantNamesLine(ticket, userNames))
                    .append("\n\nDescription:\n");
        } else {
            sb.append("Ticket ref: ")
                    .append(nullSafe(ticket.getTicketNumber()))
                    .append("\nInternal id: ")
                    .append(ticket.getId())
                    .append("\nTitle: ")
                    .append(nullSafe(ticket.getTitle()))
                    .append("\nStatus (label): ")
                    .append(uiLabels.ticketStatus(ticket.getStatus(), locale))
                    .append(" — raw code: ")
                    .append(nullSafe(ticket.getStatus()))
                    .append("\nPriority: ")
                    .append(nullSafe(ticket.getPriority()))
                    .append("\nType: ")
                    .append(nullSafe(ticket.getTicketType()))
                    .append("\nPrimary assignee (user id): ")
                    .append(ticket.getAssignedTo())
                    .append("\nAgents (usernames): ")
                    .append(participantNamesLine(ticket, userNames))
                    .append("\n\nDescription:\n");
        }
        sb.append(truncate(nullSafe(ticket.getDescription()), 4000))
                .append(french ? "\n\nTâches:\n" : "\n\nTasks:\n");
        for (AutomatedJob j : jobs.findByTicketId(ticket.getId())) {
            sb.append("- ")
                    .append(nullSafe(j.getJobTitle()))
                    .append(french ? " | statut (libellé)=" : " | status (label)=")
                    .append(uiLabels.taskStatus(jobStatusCode(j), locale))
                    .append(french ? " | code=" : " | raw=")
                    .append(jobStatusCode(j))
                    .append(" | assignee_id=")
                    .append(j.getAssigneeUserId())
                    .append(french ? " | agent=" : " | assignee=")
                    .append(j.getAssigneeUserId() == null ? "" : userNames.getOrDefault(j.getAssigneeUserId(), ""))
                    .append('\n');
        }
        sb.append(french ? "\nJournal récent (événements):\n" : "\nRecent event log:\n");
        List<TicketEvent> events = ticketEvents.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        int from = Math.max(0, events.size() - 40);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (int i = from; i < events.size(); i++) {
            TicketEvent e = events.get(i);
            sb.append("- ")
                    .append(e.getCreatedAt() == null ? "?" : fmt.format(e.getCreatedAt()))
                    .append(" | ")
                    .append(nullSafe(e.getEventType()))
                    .append(" | ")
                    .append(nullSafe(e.getNote()))
                    .append('\n');
        }
        String block = sb.toString();
        return truncate(block, MAX_CONTEXT_CHARS);
    }

    private String callOpenAiBriefing(String context, Locale locale) throws Exception {
        boolean french = locale != null && Locale.FRENCH.getLanguage().equals(locale.getLanguage());
        String system =
                french
                        ? "Tu es un assistant métier pour la douane (SYSCO). Un cadre va valider la clôture d'un ticket.\n"
                                + "Rédige un briefing professionnel, utile et dense en français (style « chef de service »).\n"
                                + "Structure obligatoire en quatre blocs courts, avec puces : (1) Synthèse du dossier en deux ou trois phrases. "
                                + "(2) Travail réalisé / état des tâches et des agents. (3) Points de vigilance ou écarts éventuels par rapport au ticket. "
                                + "(4) Recommandation prudente pour la décision (valider / demander une précision / rouvrir une tâche) sans inventer de faits.\n"
                                + "Le contexte inclut déjà des libellés humains pour les statuts : utilise-les dans ta réponse ; n'affiche pas seuls les codes techniques "
                                + "(par ex. préfère « Clôture en attente de revue » à la place de CLOSE_REQUESTED seul).\n"
                                + "Ne pas inventer de faits absents du contexte : si une information manque, dis-le explicitement.\n"
                                + "Pas de HTML ni de Markdown lourd ; texte brut uniquement."
                        : "You assist senior customs officers reviewing ticket closure in SYSCO.\n"
                                + "Write a concise, high-value briefing in English (supervisory tone).\n"
                                + "Use four short bullet-led sections: (1) Dossier summary in 2–3 sentences. (2) Work completed / task and agent state. "
                                + "(3) Watch-outs or gaps versus the ticket record. (4) Prudent closure recommendation (approve / clarify / reopen work) without inventing facts.\n"
                                + "The context includes human-readable status labels — use those in your answer instead of raw codes alone.\n"
                                + "Do not invent facts missing from the context.\n"
                                + "Plain text only; no HTML.";
        String userMsg =
                french
                        ? ("Voici les données du ticket — produis le briefing pour le réviseur :\n\n" + context)
                        : ("Here is the ticket context — produce the reviewer briefing:\n\n" + context);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", openAiModel == null ? "gpt-4o-mini" : openAiModel.trim());
        root.put("temperature", 0.35);
        root.put("max_tokens", 1100);
        ArrayNode messages = root.putArray("messages");
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", system));
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userMsg));

        String base = openAiBaseUrl == null ? "" : openAiBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        RestClient client = restClientBuilder.baseUrl(base).build();
        String rawJson =
                client.post()
                        .uri("/v1/chat/completions")
                        .header("Authorization", "Bearer " + openAiApiKey.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(root)
                        .retrieve()
                        .body(String.class);
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("empty OpenAI response");
        }
        JsonNode tree = objectMapper.readTree(rawJson);
        return tree.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…";
    }
}
