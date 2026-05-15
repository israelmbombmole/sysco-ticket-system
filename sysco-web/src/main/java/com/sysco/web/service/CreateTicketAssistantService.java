package com.sysco.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.tickets.TicketIssuePresets;
import com.sysco.web.web.dto.CreateTicketAssistantDtos.ChatResponse;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateTicketAssistantService {

    static final String SESSION_KEY = "SYSCO_CREATE_TICKET_ASSISTANT_STATE";

    private static final int MAX_MESSAGE_LEN = 8000;
    private static final int MAX_HISTORY_TURNS = 24;
    private static final Locale FR = Locale.FRENCH;
    private static final String ENSEMBLE_DIRECTION = "Ensemble direction";
    private static final String CATEGORY_OTHER = "__OTHER__";
    private static final LinkedHashMap<String, List<String>> DGDA_DIRECTION_CATALOG = new LinkedHashMap<>();
    private static final String DSTI_TOP_DIRECTION = "Direction des Systèmes et Technologies d’Information";
    private static final String DSTI_DEV_APPS = "Développement et Maintenance des Applications";
    private static final String DSTI_NETWORK_HW = "Réseaux, Télécommunications et Maintenance Hardware";
    private static final String DSTI_SYDONIA = "Sydonia";

    /** Max 15 items per list + "Autre". */
    private static final List<IssueOption> ISSUES_DEV_APPS = new ArrayList<>();
    private static final List<IssueOption> ISSUES_NETWORK_HW = new ArrayList<>();
    private static final List<IssueOption> ISSUES_SYDONIA = new ArrayList<>();
    static {
        DGDA_DIRECTION_CATALOG.put("Direction de la Réglementation et de la Facilitation", List.of("Réglementation", "Facilitation"));
        DGDA_DIRECTION_CATALOG.put("Direction de la Lutte contre la Fraude", List.of("Liaison et Renseignements", "Stratégies et Planification", "Audit a posteriori"));
        DGDA_DIRECTION_CATALOG.put("Direction du Tarif et des Règles d’Origine", List.of("Tarif", "Règles d’origine"));
        DGDA_DIRECTION_CATALOG.put("Direction de la Valeur", List.of("Évaluation", "Recours et valeurs de base"));
        DGDA_DIRECTION_CATALOG.put("Direction des Autres Produits d’Accises", List.of("Alcools, Boissons alcooliques et Limonades", "Tabacs et autres Produits d’Accises"));
        DGDA_DIRECTION_CATALOG.put("Direction des Huiles Minérales", List.of("Producteurs", "Distributeurs"));
        DGDA_DIRECTION_CATALOG.put("Direction des Recettes du Trésor", List.of("Recettes de Douanes", "Recettes des Accises", "Budget et Recettes Connexes"));
        DGDA_DIRECTION_CATALOG.put("Direction des Ressources Humaines", List.of("Recrutement et Formation", "Administration", "OEuvres Sociales", "Relations Publiques et Protocole"));
        DGDA_DIRECTION_CATALOG.put("Direction des Équipements et de la Logistique", List.of("Gestion du Patrimoine", "Imprimerie et Approvisionnements"));
        DGDA_DIRECTION_CATALOG.put("Direction des Statistiques, Documentation et Études Économiques", List.of("Statistiques et Études Économiques", "Documentation"));
        DGDA_DIRECTION_CATALOG.put("Direction des Affaires Juridiques et Contentieuses", List.of("Affaires Contentieuses", "Affaires Juridiques"));
        DGDA_DIRECTION_CATALOG.put("Direction des Systèmes et Technologies d’Information", List.of("Développement et Maintenance des Applications", "Réseaux, Télécommunications et Maintenance Hardware", "Sydonia"));
        DGDA_DIRECTION_CATALOG.put("Direction de l’Audit Interne", List.of());
        DGDA_DIRECTION_CATALOG.put("Direction des Finances Internes", List.of("Comptabilité et Trésorerie", "Budget Interne"));
        DGDA_DIRECTION_CATALOG.put("Direction des Réformes et Modernisation", List.of());
        DGDA_DIRECTION_CATALOG.put("Bureau de Coordination", List.of());

        // Universalized short lists per sous-direction (<= 15) + "Autre (saisie manuelle)".
        ISSUES_DEV_APPS.addAll(List.of(
                new IssueOption("Application qui ne démarre pas", "SOFTWARE_BUG"),
                new IssueOption("Application lente ou qui se bloque", "SOFTWARE_BUG"),
                new IssueOption("Messages d’erreur logiciels", "SOFTWARE_BUG"),
                new IssueOption("Problèmes de compatibilité logiciel/Windows", "SOFTWARE_BUG"),
                new IssueOption("Mauvais affichage des interfaces", "SOFTWARE_BUG"),
                new IssueOption("Fonctionnalités qui ne répondent pas", "SOFTWARE_BUG"),
                new IssueOption("Problèmes de connexion à une application", "ACCOUNTS_ACCESS"),
                new IssueOption("Problèmes de droits d’accès", "ACCOUNTS_ACCESS"),
                new IssueOption("Mot de passe oublié ou compte bloqué", "PASSWORD_RESET"),
                new IssueOption("Export PDF/Excel qui ne fonctionne pas", "SOFTWARE_BUG"),
                new IssueOption("Génération de rapports impossible", "SOFTWARE_BUG"),
                new IssueOption("Notifications ou emails automatiques non envoyés", "SOFTWARE_BUG"),
                new IssueOption("Installation ou désinstallation d’applications", "SOFTWARE_INSTALL"),
                new IssueOption("API ou services web indisponibles", "SOFTWARE_BUG"),
                new IssueOption("Microsoft Office qui ne fonctionne pas", "SOFTWARE_BUG"),
                new IssueOption("Autre (saisie manuelle)", CATEGORY_OTHER)));

        ISSUES_NETWORK_HW.addAll(List.of(
                new IssueOption("Absence de connexion internet", "NETWORK_WIFI"),
                new IssueOption("Réseau lent", "NETWORK_WIFI"),
                new IssueOption("Wi‑Fi indisponible / signal faible", "NETWORK_WIFI"),
                new IssueOption("Perte de connexion VPN", "NETWORK_VPN"),
                new IssueOption("Impossible d’accéder au serveur", "NETWORK_WIFI"),
                new IssueOption("Problèmes DNS / pare-feu", "NETWORK_WIFI"),
                new IssueOption("Téléphone IP / VoIP ne fonctionne pas", "HARDWARE_PHONE"),
                new IssueOption("Problèmes de visioconférence (caméra / micro)", "HARDWARE_PHONE"),
                new IssueOption("Ordinateur ne démarre pas / écran noir", "HARDWARE_PC"),
                new IssueOption("PC lent / surchauffe / ventilateur bruyant", "HARDWARE_PC"),
                new IssueOption("Imprimante / scanner / photocopieur en panne", "PRINT_SCAN"),
                new IssueOption("Ports / USB / périphériques non détectés", "HARDWARE_PC"),
                new IssueOption("Récupération de données / disque endommagé", "HARDWARE_PC"),
                new IssueOption("Virus / malware / phishing / compte compromis", "SECURITY_INCIDENT"),
                new IssueOption("Antivirus expiré / mise à jour sécurité manquante", "SECURITY_INCIDENT"),
                new IssueOption("Autre (saisie manuelle)", CATEGORY_OTHER)));

        ISSUES_SYDONIA.addAll(List.of(
                new IssueOption("Impossible de se connecter / erreur d’authentification", "ACCOUNTS_ACCESS"),
                new IssueOption("Compte utilisateur bloqué / droits d’accès", "ACCOUNTS_ACCESS"),
                new IssueOption("Mot de passe oublié", "PASSWORD_RESET"),
                new IssueOption("Session expirée trop vite", "ACCOUNTS_ACCESS"),
                new IssueOption("Déclaration douanière bloquée", "SOFTWARE_BUG"),
                new IssueOption("Validation impossible des déclarations", "SOFTWARE_BUG"),
                new IssueOption("Calcul incorrect des taxes et droits", "SOFTWARE_BUG"),
                new IssueOption("Numéro de déclaration introuvable / données perdues", "SOFTWARE_BUG"),
                new IssueOption("Lenteur du système Sydonia", "SOFTWARE_BUG"),
                new IssueOption("Erreurs lors de l’enregistrement des données", "SOFTWARE_BUG"),
                new IssueOption("Impression des documents douaniers impossible", "PRINT_SCAN"),
                new IssueOption("Synchronisation avec les autres systèmes impossible", "SOFTWARE_BUG"),
                new IssueOption("Modules Sydonia indisponibles", "SOFTWARE_BUG"),
                new IssueOption("Serveur / infrastructure Sydonia indisponible", "SOFTWARE_BUG"),
                new IssueOption("Mise à jour / correctifs Sydonia échoués", "SOFTWARE_INSTALL"),
                new IssueOption("Autre (saisie manuelle)", CATEGORY_OTHER)));
    }

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final CreateTicketHandlingRoutingService handlingRouting;
    private final SousDirectionRepository sousDirections;
    private final DirectionRepository directions;

    @Value("${sysco.assistant.openai-api-key:}")
    private String openAiApiKey;

    @Value("${sysco.assistant.openai-base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${sysco.assistant.openai-model:gpt-4o-mini}")
    private String openAiModel;

    public ChatResponse handleMessage(HttpSession session, String rawMessage, Locale locale) {
        Locale loc = locale != null ? locale : FR;
        SessionState state = (SessionState) session.getAttribute(SESSION_KEY);
        if (state == null) {
            state = new SessionState();
            session.setAttribute(SESSION_KEY, state);
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.length() > MAX_MESSAGE_LEN) {
            message = message.substring(0, MAX_MESSAGE_LEN);
        }

        if (!message.isEmpty()) {
            state.history.add(new Turn("user", message));
            trimHistory(state);
        }

        if (state.phase == Phase.START || state.phase == Phase.DGDA_DIRECTION || state.phase == Phase.DGDA_SOUS
                || state.phase == Phase.OFFICE || state.phase == Phase.DOMAIN || state.phase == Phase.ISSUE) {
            return handleScripted(state, message, loc);
        }

        if (StringUtils.hasText(openAiApiKey)) {
            try {
                return handleOpenAi(state, loc);
            } catch (Exception e) {
                log.warn("OpenAI assistant failed, falling back to scripted flow: {}", e.getMessage());
            }
        }

        return handleScripted(state, message, loc);
    }

    public void reset(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    private void trimHistory(SessionState state) {
        while (state.history.size() > MAX_HISTORY_TURNS) {
            state.history.remove(0);
        }
    }

    private ChatResponse handleOpenAi(SessionState state, Locale locale) throws Exception {
        String system = buildOpenAiSystemPrompt(locale);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", openAiModel.trim());
        root.put("temperature", 0.35);
        ArrayNode messages = root.putArray("messages");
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", system));
        if (state.history.isEmpty()) {
            messages.add(
                    objectMapper
                            .createObjectNode()
                            .put("role", "user")
                            .put("content", "Bonjour, je souhaite créer un ticket."));
        } else {
            for (Turn t : state.history) {
                messages.add(objectMapper.createObjectNode().put("role", t.role()).put("content", t.content()));
            }
        }

        String base = openAiBaseUrl == null ? "" : openAiBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        RestClient client = restClientBuilder.baseUrl(base).build();

        String rawJson =
                client
                        .post()
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
        String content =
                tree.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isEmpty()) {
            throw new IllegalStateException("no assistant content");
        }

        JsonNode parsed = extractJsonObject(objectMapper, content);
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalStateException("assistant JSON not found");
        }

        String assistantMessage = parsed.path("assistantMessage").asText("").trim();
        if (assistantMessage.isEmpty()) {
            assistantMessage = content;
        }

        boolean complete = parsed.path("conversationComplete").asBoolean(false);
        Map<String, String> prefill = readPrefill(parsed.path("prefill"));
        if (state.reporterSousDirectionId != null && state.reporterDirectionId != null) {
            prefill.put("reporterSousDirectionId", String.valueOf(state.reporterSousDirectionId));
            prefill.put("reporterDirectionId", String.valueOf(state.reporterDirectionId));
        }
        if (state.reportingOffice != null && !state.reportingOffice.isBlank()) {
            prefill.put("reportingOffice", state.reportingOffice.trim());
        }
        normalizePrefill(prefill);

        state.history.add(new Turn("assistant", assistantMessage));
        trimHistory(state);

        return new ChatResponse(assistantMessage, Map.copyOf(prefill), complete, "openai");
    }

    private static JsonNode extractJsonObject(ObjectMapper mapper, String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return mapper.readTree(content.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> readPrefill(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        putIfText(map, node, "issuePresetCode");
        putIfText(map, node, "summary");
        putIfText(map, node, "description");
        putIfText(map, node, "priority");
        putIfText(map, node, "reporterSousDirectionId");
        putIfText(map, node, "reporterDirectionId");
        putIfText(map, node, "reportingOffice");
        putIfText(map, node, "handlingSousDirectionId");
        putIfText(map, node, "handlingDirectionId");
        return map;
    }

    private static void putIfText(Map<String, String> map, JsonNode parent, String field) {
        String v = parent.path(field).asText("").trim();
        if (!v.isEmpty()) {
            map.put(field, v);
        }
    }

    private void normalizePrefill(Map<String, String> prefill) {
        String code = prefill.get("issuePresetCode");
        if (code != null && !TicketIssuePresets.isAllowed(code)) {
            prefill.remove("issuePresetCode");
        }
        String p = prefill.get("priority");
        if (p != null && !p.isBlank()) {
            prefill.put("priority", normalizePriority(p));
        }
        sanitizeReporterIds(prefill);
        applyHandlingRouting(prefill);
        sanitizeHandlingIds(prefill);
    }

    private void sanitizeReporterIds(Map<String, String> prefill) {
        String sd = prefill.get("reporterSousDirectionId");
        String d = prefill.get("reporterDirectionId");
        if (sd == null || d == null) {
            return;
        }
        try {
            long sdl = Long.parseLong(sd.trim());
            long dirl = Long.parseLong(d.trim());
            Direction dir = directions.findById(dirl).orElse(null);
            if (dir == null || dir.getSousDirectionId() == null || !dir.getSousDirectionId().equals(sdl)) {
                prefill.remove("reporterSousDirectionId");
                prefill.remove("reporterDirectionId");
            }
        } catch (NumberFormatException e) {
            prefill.remove("reporterSousDirectionId");
            prefill.remove("reporterDirectionId");
        }
    }

    /** Server-side DSTI routing from preset + text when the assistant/user did not already pick handling ids. */
    private void applyHandlingRouting(Map<String, String> prefill) {
        if (StringUtils.hasText(prefill.get("handlingSousDirectionId"))
                && StringUtils.hasText(prefill.get("handlingDirectionId"))) {
            return;
        }
        String code = prefill.get("issuePresetCode");
        handlingRouting
                .suggest(code == null ? "" : code, prefill.get("summary"), prefill.get("description"))
                .ifPresent(
                        h -> {
                            prefill.put("handlingSousDirectionId", String.valueOf(h.sousDirectionId()));
                            prefill.put("handlingDirectionId", String.valueOf(h.directionId()));
                        });
    }

    private void sanitizeHandlingIds(Map<String, String> prefill) {
        String sd = prefill.get("handlingSousDirectionId");
        String d = prefill.get("handlingDirectionId");
        if (sd == null || d == null) {
            return;
        }
        try {
            long sdl = Long.parseLong(sd.trim());
            long dirl = Long.parseLong(d.trim());
            if (!handlingRouting.isValidPair(sdl, dirl)) {
                prefill.remove("handlingSousDirectionId");
                prefill.remove("handlingDirectionId");
            }
        } catch (NumberFormatException e) {
            prefill.remove("handlingSousDirectionId");
            prefill.remove("handlingDirectionId");
        }
    }

    private String buildOpenAiSystemPrompt(Locale locale) {
        String codes = String.join(", ", TicketIssuePresets.ORDERED_CODES);
        boolean french = locale != null && "fr".equalsIgnoreCase(locale.getLanguage());
        String langLine =
                french
                        ? "Speak only French in assistantMessage."
                        : "Use the same language as the user for assistantMessage (prefer French if unsure).";
        return """
                You help users fill an external service ticket form. %s
                Collect only: issuePresetCode (one of: %s), summary (one short line), description (can be empty),
                priority as BASSE|MOYENNE|HAUTE|CRITIQUE (map to LOW|MEDIUM|HIGH|CRITICAL in prefill) or MEDIUM by default.
                First, ask reporter origin details:
                - reporterSousDirectionId (DGDA direction of origin),
                - reporterDirectionId (sous-direction of origin under that direction),
                - reportingOffice (office number / floor).
                Keep these origin fields in prefill once chosen.
                Do not mention Jira or commercial trackers. Do not ask for « département destinataire » (it is automatic).
                Ask one focused question at a time. When issue fields are ready, set conversationComplete=true.

                Respond with ONE JSON object only (no markdown fences):
                {"assistantMessage":"...","prefill":{"issuePresetCode":"","summary":"","description":"","priority":"MEDIUM"},"conversationComplete":false}
                """
                .formatted(langLine, codes);
    }

    private ChatResponse handleScripted(SessionState state, String lastUserMessage, Locale locale) {
        return switch (state.phase) {
            case START -> {
                state.phase = Phase.DGDA_DIRECTION;
                String reply =
                        messageSource.getMessage("createTicket.assistant.script.askDgdaDirection", null, locale)
                                + "\n\n"
                                + formatSousDirectionMenu(locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, Map.of(), false, "scripted");
            }
            case DGDA_DIRECTION -> {
                String topDirection = resolveTopDirection(lastUserMessage);
                if (topDirection == null) {
                    String reply =
                            messageSource.getMessage("createTicket.assistant.script.badDgdaDirection", null, locale)
                                    + "\n\n"
                                    + formatSousDirectionMenu(locale)
                                    + "\n\n"
                                    + messageSource.getMessage("createTicket.assistant.script.dgdaFooter", null, locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                Long reporterSdId = resolveReporterSousDirectionId(topDirection);
                if (reporterSdId == null) {
                    String reply =
                            messageSource.getMessage("createTicket.assistant.script.noChildDirections", null, locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                state.reporterTopDirectionName = topDirection;
                state.reporterSousDirectionId = reporterSdId;
                state.phase = Phase.DGDA_SOUS;
                String reply =
                        messageSource.getMessage("createTicket.assistant.script.askReporterSous", null, locale)
                                + "\n\n"
                                + formatDirectionMenu(topDirection, locale)
                                + "\n\n"
                                + messageSource.getMessage("createTicket.assistant.script.reporterSousFooter", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case DGDA_SOUS -> {
                if (state.reporterSousDirectionId == null || state.reporterTopDirectionName == null) {
                    state.phase = Phase.DGDA_DIRECTION;
                    String reply =
                            messageSource.getMessage("createTicket.assistant.script.badDgdaDirection", null, locale)
                                    + "\n\n"
                                    + formatSousDirectionMenu(locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                String childLabel = resolveChildDirection(state.reporterTopDirectionName, lastUserMessage);
                Long dirId = childLabel == null ? null : resolveReporterDirectionId(state.reporterTopDirectionName, childLabel);
                if (dirId == null) {
                    String reply =
                            messageSource.getMessage("createTicket.assistant.script.badReporterSous", null, locale)
                                    + "\n\n"
                                    + formatDirectionMenu(state.reporterTopDirectionName, locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                state.reporterDirectionId = dirId;
                state.phase = Phase.OFFICE;
                String reply =
                        messageSource.getMessage("createTicket.assistant.script.afterOrgOk", null, locale)
                                + "\n\n"
                                + messageSource.getMessage("createTicket.assistant.script.askOffice", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case OFFICE -> {
                if (lastUserMessage.isBlank()) {
                    String reply = messageSource.getMessage("createTicket.assistant.script.needOffice", null, locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                state.reportingOffice = lastUserMessage.trim();
                state.phase = Phase.DOMAIN;
                String reply =
                        messageSource.getMessage("createTicket.assistant.script.introIssue", null, locale)
                                + "\n\n"
                                + formatDomainMenu();
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case DOMAIN -> {
                IssueDomain domain = resolveDomain(lastUserMessage);
                if (domain == null) {
                    String reply =
                            "Je n’ai pas compris. " + "\n\n" + formatDomainMenu();
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                state.issueDomain = domain;
                state.phase = Phase.ISSUE;
                String reply = "Merci. " + "\n\n" + formatIssueMenu(domain);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case ISSUE -> {
                CategoryChoice choice = resolveCategoryChoice(state.issueDomain, lastUserMessage);
                if (choice == null) {
                    String reply = "Je n’ai pas compris. " + "\n\n" + formatIssueMenu(state.issueDomain);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, Map.of(), false, "scripted");
                }
                String code = choice.presetCode();
                state.issuePresetCode = code;
                applyDstiHandlingFromDomain(state);
                state.phase = Phase.SUMMARY;
                String reply = messageSource.getMessage("createTicket.assistant.script.askSummary", null, locale);
                if (choice.issueLabel() != null && !choice.issueLabel().isBlank()) {
                    reply = "Problème sélectionné: " + choice.issueLabel() + "\n\n" + reply;
                }
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                Map<String, String> prefill = new LinkedHashMap<>();
                prefill.put("issuePresetCode", code);
                putHandlingFromState(prefill, state);
                yield new ChatResponse(reply, Map.copyOf(prefill), false, "scripted");
            }
            case SUMMARY -> {
                if (lastUserMessage.isBlank()) {
                    String reply = messageSource.getMessage("createTicket.assistant.script.needSummary", null, locale);
                    state.history.add(new Turn("assistant", reply));
                    trimHistory(state);
                    yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
                }
                state.summary = lastUserMessage;
                state.phase = Phase.DESCRIPTION;
                String reply = messageSource.getMessage("createTicket.assistant.script.askDescription", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case DESCRIPTION -> {
                state.description = lastUserMessage.isBlank() ? "-" : lastUserMessage;
                state.phase = Phase.PRIORITY;
                String reply = messageSource.getMessage("createTicket.assistant.script.askPriority", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, partialPrefill(state), false, "scripted");
            }
            case PRIORITY -> {
                state.priority = normalizePriority(lastUserMessage);
                state.phase = Phase.DONE;
                String reply = messageSource.getMessage("createTicket.assistant.script.ready", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, completePrefill(state), true, "scripted");
            }
            case DONE -> {
                String reply = messageSource.getMessage("createTicket.assistant.script.doneHint", null, locale);
                state.history.add(new Turn("assistant", reply));
                trimHistory(state);
                yield new ChatResponse(reply, completePrefill(state), true, "scripted");
            }
        };
    }

    private Map<String, String> partialPrefill(SessionState state) {
        Map<String, String> m = new LinkedHashMap<>();
        putReporterFromState(m, state);
        if (state.issuePresetCode != null) {
            m.put("issuePresetCode", state.issuePresetCode);
        }
        if (state.summary != null) {
            m.put("summary", state.summary);
        }
        if (state.description != null) {
            m.put("description", state.description);
        }
        putHandlingFromState(m, state);
        return m;
    }

    private Map<String, String> completePrefill(SessionState state) {
        Map<String, String> m = new LinkedHashMap<>();
        putReporterFromState(m, state);
        m.put("issuePresetCode", nz(state.issuePresetCode));
        m.put("summary", nz(state.summary));
        m.put("description", nz(state.description));
        m.put("priority", nz(state.priority));
        putHandlingFromState(m, state);
        return m;
    }

    private void putHandlingFromState(Map<String, String> m, SessionState state) {
        if (state.handlingSousDirectionId != null && state.handlingDirectionId != null) {
            m.put("handlingSousDirectionId", String.valueOf(state.handlingSousDirectionId));
            m.put("handlingDirectionId", String.valueOf(state.handlingDirectionId));
            return;
        }
        handlingRouting.suggest(state.issuePresetCode, state.summary, state.description).ifPresent(h -> {
            m.put("handlingSousDirectionId", String.valueOf(h.sousDirectionId()));
            m.put("handlingDirectionId", String.valueOf(h.directionId()));
        });
    }

    private void putReporterFromState(Map<String, String> m, SessionState state) {
        if (state.reporterSousDirectionId != null) {
            m.put("reporterSousDirectionId", String.valueOf(state.reporterSousDirectionId));
        }
        if (state.reporterDirectionId != null) {
            m.put("reporterDirectionId", String.valueOf(state.reporterDirectionId));
        }
        if (state.reportingOffice != null && !state.reportingOffice.isBlank()) {
            m.put("reportingOffice", state.reportingOffice.trim());
        }
    }

    private String formatSousDirectionMenu(Locale locale) {
        StringBuilder sb = new StringBuilder();
        List<String> rows = new ArrayList<>(DGDA_DIRECTION_CATALOG.keySet());
        for (int i = 0; i < rows.size(); i++) {
            sb.append(i + 1).append(". ").append(rows.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatDirectionMenu(String topDirectionName, Locale locale) {
        StringBuilder sb = new StringBuilder();
        List<String> rows = childLabelsForTopDirection(topDirectionName);
        for (int i = 0; i < rows.size(); i++) {
            sb.append(i + 1).append(". ").append(rows.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private String resolveTopDirection(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String t = input.trim();
        List<String> rows = new ArrayList<>(DGDA_DIRECTION_CATALOG.keySet());
        if (t.matches("^\\d+$")) {
            int idx = Integer.parseInt(t) - 1;
            return idx >= 0 && idx < rows.size() ? rows.get(idx) : null;
        }
        String lower = normalizeDirectionName(t);
        List<String> matches = rows.stream()
                .filter(r -> normalizeDirectionName(r).contains(lower))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String resolveChildDirection(String topDirectionName, String input) {
        if (input == null || input.isBlank() || topDirectionName == null) {
            return null;
        }
        String t = input.trim();
        List<String> rows = childLabelsForTopDirection(topDirectionName);
        if (t.matches("^\\d+$")) {
            int idx = Integer.parseInt(t) - 1;
            return idx >= 0 && idx < rows.size() ? rows.get(idx) : null;
        }
        String lower = normalizeDirectionName(t);
        List<String> matches = rows.stream()
                .filter(r -> normalizeDirectionName(r).contains(lower))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<String> childLabelsForTopDirection(String topDirectionName) {
        List<String> rows = DGDA_DIRECTION_CATALOG.getOrDefault(topDirectionName, List.of());
        if (rows.isEmpty()) {
            return List.of(ENSEMBLE_DIRECTION);
        }
        return rows;
    }

    private Long resolveReporterSousDirectionId(String topDirectionName) {
        if (topDirectionName == null) {
            return null;
        }
        return sousDirections.findAll().stream()
                .filter(sd -> normalizeDirectionName(sd.getName()).equals(normalizeDirectionName(topDirectionName)))
                .map(SousDirection::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveReporterDirectionId(String topDirectionName, String childLabel) {
        Long sdId = resolveReporterSousDirectionId(topDirectionName);
        if (sdId == null) {
            return null;
        }
        String childNorm = normalizeDirectionName(childLabel);
        List<Direction> candidates = directions.findAllBySousDirectionIdOrderByNameAsc(sdId);
        for (Direction d : candidates) {
            String base = canonicalBaseName(d.getName());
            String leaf = canonicalChildName(d.getName());
            if (!normalizeDirectionName(topDirectionName).equals(base)) {
                continue;
            }
            if (ENSEMBLE_DIRECTION.equalsIgnoreCase(childLabel)) {
                if (leaf.isBlank() || "ensemble direction".equals(leaf)) {
                    return d.getId();
                }
            } else if (leaf.equals(childNorm)) {
                return d.getId();
            }
        }
        if (ENSEMBLE_DIRECTION.equalsIgnoreCase(childLabel)) {
            return candidates.stream().map(Direction::getId).findFirst().orElse(null);
        }
        return null;
    }

    private static String canonicalBaseName(String raw) {
        String n = normalizeDirectionName(raw);
        int idx = n.indexOf(" — ");
        return idx > 0 ? n.substring(0, idx).trim() : n;
    }

    private static String canonicalChildName(String raw) {
        String n = normalizeDirectionName(raw);
        int idx = n.indexOf(" — ");
        return idx > 0 ? n.substring(idx + 3).trim() : "";
    }

    /**
     * Automatically prefill DGDA handling direction/sous-direction for DSTI issues based on the selected domain.
     * This makes the routing fields appear filled immediately after the issue selection.
     */
    private void applyDstiHandlingFromDomain(SessionState state) {
        if (state == null || state.issueDomain == null) {
            return;
        }
        Long handlingSdId = resolveSousDirectionIdByName(DSTI_TOP_DIRECTION);
        if (handlingSdId == null) {
            return;
        }
        String child =
                switch (state.issueDomain) {
                    case APPLICATION -> DSTI_DEV_APPS;
                    case NETWORK_HW -> DSTI_NETWORK_HW;
                    case SYDONIA -> DSTI_SYDONIA;
                };
        Long handlingDirId = resolveDirectionIdByTopAndChildName(DSTI_TOP_DIRECTION, child);
        if (handlingDirId == null) {
            return;
        }
        state.handlingSousDirectionId = handlingSdId;
        state.handlingDirectionId = handlingDirId;
    }

    private Long resolveSousDirectionIdByName(String sousDirectionName) {
        if (sousDirectionName == null || sousDirectionName.isBlank()) {
            return null;
        }
        String target = normalizeDirectionName(sousDirectionName);
        return sousDirections.findAll().stream()
                .filter(sd -> normalizeDirectionName(sd.getName()).equals(target))
                .map(SousDirection::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveDirectionIdByTopAndChildName(String topDirectionName, String childLabel) {
        Long sdId = resolveSousDirectionIdByName(topDirectionName);
        if (sdId == null) {
            return null;
        }
        String childNorm = normalizeDirectionName(childLabel);
        List<Direction> candidates = directions.findAllBySousDirectionIdOrderByNameAsc(sdId);
        for (Direction d : candidates) {
            String base = canonicalBaseName(d.getName());
            String leaf = canonicalChildName(d.getName());
            if (!normalizeDirectionName(topDirectionName).equals(base)) {
                continue;
            }
            if (leaf.equals(childNorm)) {
                return d.getId();
            }
        }
        return null;
    }

    private String formatDomainMenu() {
        return """
                L’incident que vous rencontrez concerne plutôt :
                1. Applications (Développement et Maintenance des Applications)
                2. Réseau / Télécom / Matériel (Réseaux, Télécommunications et Maintenance Hardware)
                3. Sydonia

                Répondez par 1, 2 ou 3.
                """
                .trim();
    }

    private String formatIssueMenu(IssueDomain domain) {
        List<IssueOption> issues = issuesForDomain(domain);
        String title =
                switch (domain) {
                    case APPLICATION -> "Choisissez le problème (Applications) :";
                    case NETWORK_HW -> "Choisissez le problème (Réseau / Télécom / Matériel) :";
                    case SYDONIA -> "Choisissez le problème (Sydonia) :";
                };

        StringBuilder out = new StringBuilder();
        out.append(title).append("\n\n");
        for (int i = 0; i < issues.size(); i++) {
            out.append(i + 1).append(". ").append(issues.get(i).label()).append('\n');
        }
        out.append(
                "\nVous pouvez répondre par le numéro ou coller l’intitulé exact. "
                        + "Si votre problème n’est pas dans la liste, choisissez « Autre (saisie manuelle) ».");
        return out.toString().trim();
    }

    private List<IssueOption> issuesForDomain(IssueDomain domain) {
        if (domain == null) {
            return List.of();
        }
        return switch (domain) {
            case APPLICATION -> ISSUES_DEV_APPS;
            case NETWORK_HW -> ISSUES_NETWORK_HW;
            case SYDONIA -> ISSUES_SYDONIA;
        };
    }

    private IssueDomain resolveDomain(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String t = input.trim().toLowerCase(Locale.FRENCH);
        if (t.matches("^\\d+$")) {
            return switch (t) {
                case "1" -> IssueDomain.APPLICATION;
                case "2" -> IssueDomain.NETWORK_HW;
                case "3" -> IssueDomain.SYDONIA;
                default -> null;
            };
        }
        if (t.contains("app") || t.contains("application") || t.contains("logiciel")) {
            return IssueDomain.APPLICATION;
        }
        if (t.contains("reseau") || t.contains("réseau") || t.contains("wifi") || t.contains("vpn")
                || t.contains("telecom") || t.contains("télécom") || t.contains("materiel") || t.contains("matériel")) {
            return IssueDomain.NETWORK_HW;
        }
        if (t.contains("sydonia") || t.contains("asycuda")) {
            return IssueDomain.SYDONIA;
        }
        return null;
    }

    private CategoryChoice resolveCategoryChoice(IssueDomain domain, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        List<IssueOption> issues = issuesForDomain(domain);
        if (!issues.isEmpty()) {
            String t = input.trim();
            if (t.matches("^\\d+$")) {
                int idx = Integer.parseInt(t) - 1;
                if (idx >= 0 && idx < issues.size()) {
                    IssueOption opt = issues.get(idx);
                    return new CategoryChoice(
                            CATEGORY_OTHER.equals(opt.presetCode()) ? TicketIssuePresets.OTHER : opt.presetCode(),
                            CATEGORY_OTHER.equals(opt.presetCode()) ? null : opt.label());
                }
                return null;
            }
            String normInput = normalizeDirectionName(t);
            for (IssueOption opt : issues) {
                if (normalizeDirectionName(opt.label()).equals(normInput)) {
                    return new CategoryChoice(
                            CATEGORY_OTHER.equals(opt.presetCode()) ? TicketIssuePresets.OTHER : opt.presetCode(),
                            CATEGORY_OTHER.equals(opt.presetCode()) ? null : opt.label());
                }
            }
        }
        // No match in the short list: we intentionally keep the user constrained to the menu.
        return null;
    }

    private String resolvePresetCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String t = input.trim();
        if (t.matches("^\\d+$")) {
            int idx = Integer.parseInt(t) - 1;
            if (idx >= 0 && idx < TicketIssuePresets.ORDERED_CODES.size()) {
                return TicketIssuePresets.ORDERED_CODES.get(idx);
            }
            return null;
        }
        String upper = t.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (TicketIssuePresets.isAllowed(upper)) {
            return upper;
        }
        String lower = t.toLowerCase(Locale.FRENCH);
        if (lower.contains("vpn")) {
            return "NETWORK_VPN";
        }
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("réseau") || lower.contains("reseau")) {
            return "NETWORK_WIFI";
        }
        if (lower.contains("imprim") || lower.contains("scan")) {
            return "PRINT_SCAN";
        }
        if (lower.contains("mot de passe") || lower.contains("password") || lower.contains("otp")) {
            return "PASSWORD_RESET";
        }
        if (lower.contains("mail") || lower.contains("outlook") || lower.contains("calendrier")) {
            return "EMAIL_CALENDAR";
        }
        if (lower.contains("compte") || lower.contains("accès") || lower.contains("acces") || lower.contains("locked")) {
            return "ACCOUNTS_ACCESS";
        }
        if (lower.contains("logiciel") || lower.contains("install")) {
            return "SOFTWARE_INSTALL";
        }
        if (lower.contains("bug") || lower.contains("plant") || lower.contains("crash")) {
            return "SOFTWARE_BUG";
        }
        if (lower.contains("formation") || lower.contains("training")) {
            return "TRAINING_REQUEST";
        }
        if (lower.contains("sécurité") || lower.contains("securite") || lower.contains("phishing")) {
            return "SECURITY_INCIDENT";
        }
        if (lower.contains("pc") || lower.contains("ordinateur") || lower.contains("laptop")) {
            return "HARDWARE_PC";
        }
        if (lower.contains("téléphone") || lower.contains("telephone") || lower.contains("mobile")) {
            return "HARDWARE_PHONE";
        }
        if (lower.contains("partage") || lower.contains("dossier") || lower.contains("share")) {
            return "DATA_SHARE_ACCESS";
        }
        if (lower.contains("portail") || lower.contains("formulaire") || lower.contains("web")) {
            return "PORTAL_WEB";
        }
        if (lower.contains("bureau") && (lower.contains("bâtiment") || lower.contains("batiment") || lower.contains("badge"))) {
            return "FACILITY_OFFICE";
        }
        if (lower.contains("autre") || lower.contains("other")) {
            return TicketIssuePresets.OTHER;
        }
        return null;
    }

    private static String normalizePriority(String input) {
        if (input == null || input.isBlank()) {
            return "MEDIUM";
        }
        String lower = input.trim().toLowerCase(Locale.FRENCH);
        String u = input.trim().toUpperCase(Locale.ROOT);
        if (lower.contains("critique") || u.contains("CRITICAL") || u.contains("CRIT") || lower.contains("urgence")) {
            return "CRITICAL";
        }
        if (lower.contains("haute") || lower.contains("élev") || lower.contains("elev") || u.contains("HIGH")) {
            return "HIGH";
        }
        if (lower.contains("basse") || u.contains("LOW")) {
            return "LOW";
        }
        if (lower.contains("moyenne") || lower.contains("moyen") || u.contains("MEDIUM") || u.contains("NORMAL")) {
            return "MEDIUM";
        }
        return "MEDIUM";
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normalizeDirectionName(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.FRENCH)
                .trim();
    }

    private enum Phase {
        START,
        DGDA_DIRECTION,
        DGDA_SOUS,
        OFFICE,
        DOMAIN,
        ISSUE,
        SUMMARY,
        DESCRIPTION,
        PRIORITY,
        DONE
    }

    /** Serializable session state */
    public static final class SessionState implements java.io.Serializable {
        private static final long serialVersionUID = 2L;

        private Phase phase = Phase.START;
        private String issuePresetCode;
        private String summary;
        private String description;
        private String reportingOffice;
        private String priority = "MEDIUM";
        private String reporterTopDirectionName;
        private IssueDomain issueDomain;
        private Long reporterSousDirectionId;
        private Long reporterDirectionId;
        private Long handlingSousDirectionId;
        private Long handlingDirectionId;
        private final ArrayList<Turn> history = new ArrayList<>();
    }

    public record Turn(String role, String content) implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    private record IssueOption(String label, String presetCode) {}

    private record CategoryChoice(String presetCode, String issueLabel) {}

    private enum IssueDomain {
        APPLICATION,
        NETWORK_HW,
        SYDONIA
    }
}
