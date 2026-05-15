package com.sysco.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sysco.web.web.dto.MissionAssistantDtos.MissionAssistantResponse;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public class MissionAssistantService {

    private static final int MAX_MESSAGE_LEN = 6000;
    private static final int MAX_CONTEXT_LEN = 8000;
    private static final int MAX_PREFILL_VALUE_LEN = 4000;

    private static final Set<String> ALLOWED_PREFILL_KEYS =
            Set.of(
                    "title",
                    "site",
                    "startDate",
                    "endDate",
                    "status",
                    "objectives",
                    "durationNote",
                    "departureNote",
                    "returnNote",
                    "transportDetail",
                    "expensesNote",
                    "description",
                    "observationsNote",
                    "orderReference",
                    "orderIssueDate",
                    "orderIssuedBy",
                    "orderBody");

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final MessageSource messageSource;

    @Value("${sysco.assistant.openai-api-key:}")
    private String openAiApiKey;

    @Value("${sysco.assistant.openai-base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${sysco.assistant.openai-model:gpt-4o-mini}")
    private String openAiModel;

    private enum Topic {
        TRANSPORT,
        DATES,
        SUBJECT,
        PARTICIPANTS,
        EXPENSES,
        ORDER_META,
        GENERIC
    }

    /** Whether an OpenAI key is configured (live completions); exposed for missions UI banner. */
    public boolean isLiveAiConfigured() {
        return StringUtils.hasText(openAiApiKey);
    }

    public MissionAssistantResponse reply(String rawMessage, String rawContext, Locale locale) {
        Locale loc = locale != null ? locale : Locale.FRENCH;
        String message = truncate(rawMessage == null ? "" : rawMessage.trim(), MAX_MESSAGE_LEN);
        String context = truncate(rawContext == null ? "" : rawContext.trim(), MAX_CONTEXT_LEN);
        Map<String, String> ctxMap = parseContext(context);

        if (!StringUtils.hasText(openAiApiKey)) {
            return smartLocalReply(message, ctxMap, loc);
        }
        try {
            String aiRaw = callOpenAi(message, context, loc);
            ParsedStructured parsed = parseStructuredAiResponse(aiRaw);
            if (parsed != null && StringUtils.hasText(parsed.reply())) {
                return new MissionAssistantResponse(
                        parsed.reply().trim(), "openai", sanitizePrefill(parsed.prefill()));
            }
            if (StringUtils.hasText(aiRaw)) {
                return new MissionAssistantResponse(aiRaw.trim(), "openai", Collections.emptyMap());
            }
        } catch (Exception e) {
            log.warn("Mission assistant OpenAI failed: {}", e.getMessage());
        }
        return smartLocalReply(message, ctxMap, loc);
    }

    private MissionAssistantResponse smartLocalReply(String message, Map<String, String> ctx, Locale locale) {
        if (!StringUtils.hasText(message)) {
            String welcome = messageSource.getMessage("missions.assistant.topic.empty", null, locale);
            String hint = messageSource.getMessage("missions.assistant.topic.generic", null, locale);
            return new MissionAssistantResponse(welcome + "\n\n" + hint, "local", Collections.emptyMap());
        }

        Topic topic = classifyTopic(message);
        return switch (topic) {
            case TRANSPORT -> transportTopic(ctx, locale);
            case DATES -> datesTopic(ctx, locale);
            case SUBJECT -> subjectTopic(ctx, locale);
            case PARTICIPANTS -> participantsTopic(locale);
            case EXPENSES -> expensesTopic(locale);
            case ORDER_META -> orderMetaTopic(locale);
            case GENERIC -> genericTopic(locale);
        };
    }

    private MissionAssistantResponse transportTopic(Map<String, String> ctx, Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.transport.reply", null, locale);
        Map<String, String> prefill = new LinkedHashMap<>();

        if (!StringUtils.hasText(ctx.get("transportDetail"))) {
            String site = dashIfBlank(ctx.get("site"));
            String sd = dashIfBlank(ctx.get("startDate"));
            String ed = dashIfBlank(ctx.get("endDate"));
            String tpl =
                    messageSource.getMessage(
                            "missions.assistant.prefill.transportDetail.template",
                            new Object[] {site, sd, ed},
                            locale);
            prefill.put("transportDetail", tpl);
        }
        if (!StringUtils.hasText(ctx.get("departureNote")) && StringUtils.hasText(ctx.get("startDate"))) {
            prefill.put(
                    "departureNote",
                    messageSource.getMessage(
                            "missions.assistant.prefill.departureNote.template",
                            new Object[] {dashIfBlank(ctx.get("startDate"))},
                            locale));
        }
        if (!StringUtils.hasText(ctx.get("returnNote")) && StringUtils.hasText(ctx.get("endDate"))) {
            prefill.put(
                    "returnNote",
                    messageSource.getMessage(
                            "missions.assistant.prefill.returnNote.template",
                            new Object[] {dashIfBlank(ctx.get("endDate"))},
                            locale));
        }
        return new MissionAssistantResponse(reply, "local", sanitizePrefill(prefill));
    }

    private MissionAssistantResponse datesTopic(Map<String, String> ctx, Locale locale) {
        StringBuilder reply = new StringBuilder();
        reply.append(messageSource.getMessage("missions.assistant.topic.dates.intro", null, locale));

        LocalDate start = parseIsoDate(ctx.get("startDate"));
        LocalDate end = parseIsoDate(ctx.get("endDate"));
        String site = nz(ctx.get("site"));
        String durationNote = nz(ctx.get("durationNote"));

        if (start != null && end != null) {
            if (end.isBefore(start)) {
                reply.append('\n')
                        .append(
                                messageSource.getMessage(
                                        "missions.assistant.topic.dates.badOrder",
                                        new Object[] {start.toString(), end.toString()},
                                        locale));
            } else {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                reply.append('\n')
                        .append(
                                messageSource.getMessage(
                                        "missions.assistant.topic.dates.okRange",
                                        new Object[] {start.toString(), end.toString(), days},
                                        locale));
                if (!StringUtils.hasText(durationNote)) {
                    Map<String, String> prefill = new LinkedHashMap<>();
                    prefill.put(
                            "durationNote",
                            messageSource.getMessage(
                                    "missions.assistant.prefill.durationNote.fromDates",
                                    new Object[] {days},
                                    locale));
                    reply.append('\n')
                            .append(messageSource.getMessage("missions.assistant.topic.dates.durationHint", null, locale));
                    return new MissionAssistantResponse(reply.toString(), "local", sanitizePrefill(prefill));
                }
            }
        } else if ((start != null) != (end != null)) {
            reply.append('\n')
                    .append(messageSource.getMessage("missions.assistant.topic.dates.needOtherDate", null, locale));
        } else {
            reply.append('\n').append(messageSource.getMessage("missions.assistant.topic.dates.needDates", null, locale));
        }

        if (!StringUtils.hasText(site)) {
            reply.append('\n').append(messageSource.getMessage("missions.assistant.topic.dates.siteMissing", null, locale));
        } else {
            reply.append('\n').append(messageSource.getMessage("missions.assistant.topic.dates.siteHint", null, locale));
        }

        if (StringUtils.hasText(durationNote)) {
            reply.append('\n')
                    .append(
                            messageSource.getMessage(
                                    "missions.assistant.topic.dates.durationProvided",
                                    new Object[] {durationNote},
                                    locale));
        } else {
            reply.append('\n').append(messageSource.getMessage("missions.assistant.topic.dates.durationHint", null, locale));
        }

        return new MissionAssistantResponse(reply.toString(), "local", Collections.emptyMap());
    }

    private MissionAssistantResponse subjectTopic(Map<String, String> ctx, Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.subject.reply", null, locale);
        Map<String, String> prefill = new LinkedHashMap<>();
        if (!StringUtils.hasText(ctx.get("objectives"))) {
            String suggestion =
                    messageSource.getMessage(
                            "missions.assistant.prefill.objectives.template",
                            new Object[] {
                                dashIfBlank(ctx.get("title")),
                                dashIfBlank(ctx.get("site")),
                                dashIfBlank(ctx.get("startDate")),
                                dashIfBlank(ctx.get("endDate"))
                            },
                            locale);
            if (StringUtils.hasText(suggestion)) {
                prefill.put("objectives", suggestion);
            }
        }
        return new MissionAssistantResponse(reply, "local", sanitizePrefill(prefill));
    }

    private MissionAssistantResponse participantsTopic(Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.participants.reply", null, locale);
        return new MissionAssistantResponse(reply, "local", Collections.emptyMap());
    }

    private MissionAssistantResponse expensesTopic(Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.expenses.reply", null, locale);
        Map<String, String> prefill = new LinkedHashMap<>();
        prefill.put(
                "expensesNote",
                messageSource.getMessage("missions.assistant.prefill.expensesNote.template", null, locale));
        return new MissionAssistantResponse(reply, "local", sanitizePrefill(prefill));
    }

    private MissionAssistantResponse orderMetaTopic(Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.orderMeta.reply", null, locale);
        return new MissionAssistantResponse(reply, "local", Collections.emptyMap());
    }

    private MissionAssistantResponse genericTopic(Locale locale) {
        String reply = messageSource.getMessage("missions.assistant.topic.generic", null, locale);
        return new MissionAssistantResponse(reply, "local", Collections.emptyMap());
    }

    private static Topic classifyTopic(String message) {
        String m = fold(message);
        if (matches(
                m,
                "transport",
                "itineraire",
                "trajet",
                "etape",
                "etapes",
                "route",
                "vehicule",
                "convoyage",
                "logistique",
                "itinerary",
                "multi-leg",
                "multi leg")) {
            return Topic.TRANSPORT;
        }
        if (matches(
                        m,
                        "date",
                        "dates",
                        "duree",
                        "coherent",
                        "coherence",
                        "mention",
                        "lieu",
                        "periode",
                        "debut",
                        "fin",
                        "consistent")
                || (m.contains("jour") && m.contains("coh"))) {
            return Topic.DATES;
        }
        if (matches(
                m,
                "objet",
                "redige",
                "redaction",
                "ordonnance",
                "order body",
                "mission pour",
                "purpose",
                "phrase",
                "wording",
                "official order")) {
            return Topic.SUBJECT;
        }
        if (matches(m, "participant", "agents", "messieurs", "mesdames", "liste des")) {
            return Topic.PARTICIPANTS;
        }
        if (matches(m, "frais", "depense", "prise en charge", "budget", "note frais")) {
            return Topic.EXPENSES;
        }
        if (matches(m, "reference", "signataire", "emission", "docx", "ordre officiel")) {
            return Topic.ORDER_META;
        }
        return Topic.GENERIC;
    }

    private static boolean matches(String foldedMessage, String... needles) {
        for (String n : needles) {
            if (foldedMessage.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String fold(String s) {
        if (s == null) {
            return "";
        }
        /* NFD splits accents into combining marks; \p{M} strips them (portable; IsCombiningDiacriticalMarks is not on all JDK regex builds). */
        String n =
                Normalizer.normalize(s.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private static LocalDate parseIsoDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Map<String, String> parseContext(String context) {
        Map<String, String> m = new LinkedHashMap<>();
        if (!StringUtils.hasText(context)) {
            return m;
        }
        for (String line : context.split("\n")) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            if (!key.isEmpty()) {
                m.put(key, val);
            }
        }
        return m;
    }

    private Map<String, String> sanitizePrefill(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = e.getKey();
            if (!ALLOWED_PREFILL_KEYS.contains(k)) {
                continue;
            }
            String v = e.getValue();
            if (!StringUtils.hasText(v)) {
                continue;
            }
            out.put(k, truncate(v.trim(), MAX_PREFILL_VALUE_LEN));
        }
        return Collections.unmodifiableMap(out);
    }

    private record ParsedStructured(String reply, Map<String, String> prefill) {}

    private ParsedStructured parseStructuredAiResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = stripMarkdownCodeFence(raw.trim());
        JsonNode root = tryParseJson(cleaned);
        if (root == null || !root.isObject()) {
            int a = cleaned.indexOf('{');
            int b = cleaned.lastIndexOf('}');
            if (a >= 0 && b > a) {
                root = tryParseJson(cleaned.substring(a, b + 1));
            }
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        String reply = root.path("reply").asText("");
        Map<String, String> prefill = new LinkedHashMap<>();
        JsonNode p = root.path("prefill");
        if (p.isObject()) {
            p.fields()
                    .forEachRemaining(
                            entry -> {
                                JsonNode v = entry.getValue();
                                if (v.isTextual()) {
                                    prefill.put(entry.getKey(), v.asText());
                                } else if (v.isValueNode()) {
                                    prefill.put(entry.getKey(), v.asText());
                                }
                            });
        }
        return new ParsedStructured(reply, prefill);
    }

    private JsonNode tryParseJson(String s) {
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripMarkdownCodeFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int nl = s.indexOf('\n');
        if (nl < 0) {
            return s;
        }
        String body = s.substring(nl + 1);
        int fence = body.lastIndexOf("```");
        if (fence >= 0) {
            body = body.substring(0, fence);
        }
        return body.trim();
    }

    private String callOpenAi(String message, String context, Locale locale) throws Exception {
        boolean french = Locale.FRENCH.getLanguage().equals(locale.getLanguage());
        String system =
                french
                        ? "Tu es un assistant métier pour les missions terrain et les ordres de mission au sein de SYSCO (contexte douanier DGDA).\n"
                                + "Réponds EN PRIORITÉ à la question précise de l'utilisateur : ne répète pas une liste générique d'étapes si une réponse ciblée suffit.\n"
                                + "Tu peux proposer du texte prêt à coller dans les champs du formulaire lorsque c'est utile.\n"
                                + "Réponds STRICTEMENT en JSON UTF-8 compact (sans markdown ni ```), schéma :\n"
                                + "{\"reply\":\"texte affiché à l'utilisateur\",\"prefill\":{\"clé\":\"valeur\",...}}\n"
                                + "Clés autorisées dans prefill (omettre les entrées vides) : title, site, startDate, endDate, status, objectives, "
                                + "durationNote, departureNote, returnNote, transportDetail, expensesNote, description, observationsNote, "
                                + "orderReference, orderIssueDate, orderIssuedBy, orderBody.\n"
                                + "Les dates au format AAAA-MM-JJ si tu les déduis.\n"
                                + "N'invente pas de faits absents du message utilisateur ou du contexte formulaire.\n"
                                + "Ne cite pas de textes légaux inventés ; si une règle précise est incertaine, dis-le dans reply."
                        : "You assist SYSCO users drafting DGDA-style field missions and mission orders.\n"
                                + "Answer the user's SPECIFIC question first—do not paste a generic workflow checklist unless they asked for an overview.\n"
                                + "You may suggest ready-to-paste values for form fields when helpful.\n"
                                + "Reply STRICTLY as compact UTF-8 JSON (no markdown fences), schema:\n"
                                + "{\"reply\":\"text shown to the user\",\"prefill\":{\"field\":\"value\",...}}\n"
                                + "Allowed prefill keys (omit empty entries): title, site, startDate, endDate, status, objectives, "
                                + "durationNote, departureNote, returnNote, transportDetail, expensesNote, description, observationsNote, "
                                + "orderReference, orderIssueDate, orderIssuedBy, orderBody.\n"
                                + "Express dates as YYYY-MM-DD if inferred.\n"
                                + "Use only facts present in the user message or form context.\n"
                                + "Do not invent legal citations; say when unsure inside reply.";

        String userPart =
                french
                        ? ("Question ou demande de l'utilisateur :\n" + (message.isEmpty() ? "(vide)" : message))
                        : ("User question or request:\n" + (message.isEmpty() ? "(empty)" : message));
        String ctxPart =
                french
                        ? ("\n\n--- Contexte copié depuis le formulaire mission (peut être vide) ---\n"
                                + (context.isEmpty() ? "(vide)" : context))
                        : ("\n\n--- Snippet from the mission form (may be empty) ---\n"
                                + (context.isEmpty() ? "(empty)" : context));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", openAiModel == null ? "gpt-4o-mini" : openAiModel.trim());
        root.put("temperature", 0.45);
        root.put("max_tokens", 1100);
        ArrayNode messages = root.putArray("messages");
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", system));
        messages.add(
                objectMapper
                        .createObjectNode()
                        .put("role", "user")
                        .put("content", userPart + ctxPart));

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
        String content = tree.path("choices").path(0).path("message").path("content").asText("").trim();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("empty OpenAI message content");
        }
        return content;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max) + "\n…";
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String dashIfBlank(String s) {
        return StringUtils.hasText(s) ? s.trim() : "—";
    }
}
