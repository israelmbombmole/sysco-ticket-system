package com.sysco.web.service;

import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.tickets.TicketIssuePresets;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Suggests DGDA handling routings for the create-ticket assistant from {@link TicketIssuePresets}
 * and free-text keywords (summary, description, preset-derived hints).
 */
@Service
@RequiredArgsConstructor
public class CreateTicketHandlingRoutingService {

    public static final String DSTI_TOP_NAME = "Direction des Systèmes et Technologies d'Information";

    /** @deprecated use {@link #DSTI_TOP_NAME} */
    @Deprecated(forRemoval = false)
    public static final String DSTI_SOUS_DIRECTION_NAME = DSTI_TOP_NAME;

    static final String NETWORK_DIRECTION_NAME =
            "Réseaux, Télécommunications et Maintenance Hardware";

    static final String SOFTWARE_DIRECTION_NAME = "Développement et Maintenance des Applications";

    static final String DGDA_SEPARATOR = "—";
    static final String ENSEMBLE_DIRECTION_NAME = "Ensemble direction";

    private static final Set<String> NETWORK_PRESETS =
            Set.of("NETWORK_WIFI", "NETWORK_VPN", "PRINT_SCAN", "HARDWARE_PC", "HARDWARE_PHONE");

    private static final Set<String> SOFTWARE_PRESETS = Set.of("SOFTWARE_INSTALL", "SOFTWARE_BUG");

    /** IT-ish presets: route to DSTI; sous-direction from text. */
    private static final Set<String> GENERIC_IT_PRESETS =
            Set.of(
                    "ACCOUNTS_ACCESS",
                    "PASSWORD_RESET",
                    "EMAIL_CALENDAR",
                    "DATA_SHARE_ACCESS",
                    "PORTAL_WEB");

    /** Non-IT / physical facility — do not auto-route. */
    private static final Set<String> NO_AUTO_ROUTE_PRESETS = Set.of("FACILITY_OFFICE");

    /** « donne », « données », etc. — avoids matching inside words like « redonner ». */
    private static final Pattern DONNEES_FRAGMENT =
            Pattern.compile("(^|\\s)(donnees|donnee|donnes|donne)(\\s|$)");

    private static final Set<String> SYDONIA_CHILD_KEYWORDS = Set.of(
            "sydonia",
            "asycuda",
            "asycuda world",
            "donnees",
            "donnee",
            "bdd",
            "base de donnees",
            "manifeste",
            "declaration en douane",
            "declaration douaniere",
            "dedouanement",
            "liquidation",
            "connaissement",
            "bon a enlever",
            "bae",
            "sw trans",
            "ventilation",
            "dau",
            "idpc");

    private static final Set<String> SOFTWARE_CHILD_KEYWORDS = Set.of(
            "software",
            "logiciel",
            "programme",
            "application",
            " appli ",
            "developpement",
            "développement",
            "maintenance applicative",
            "bug",
            "crash",
            "plantage",
            "installation",
            "installer",
            "patch",
            "version",
            "mise a jour",
            "mise à jour",
            "executable",
            "microsoft 365",
            "office",
            "excel",
            "outlook",
            "sap",
            "erp",
            "java",
            "python",
            "javascript",
            "navigateur",
            "browser",
            "windows update");

    private static final Set<String> NETWORK_CHILD_KEYWORDS = Set.of(
            "pc issue",
            "probleme pc",
            "ordinateur",
            "laptop",
            "poste de travail",
            "workstation",
            "imprimante",
            "printer",
            "scanner",
            "photocopieuse",
            "wifi",
            "wi-fi",
            "reseau",
            "réseau",
            "ethernet",
            "vpn",
            "lan",
            "dns",
            "firewall",
            "pare-feu",
            "pare feu",
            "routeur",
            "router",
            "switch",
            "fibre",
            "cable reseau",
            "telephon",
            "téléphon",
            "gsm",
            "mobile",
            "hardware",
            "materiel",
            "matériel",
            "peripherique",
            "périphérique",
            "ecran bleu",
            "écran bleu",
            "bios",
            "disque dur",
            "ssd",
            "ram",
            "usb",
            "bluetooth",
            "ip fixe",
            "adresse ip");

    private static final Map<String, Set<String>> TOP_DIRECTION_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Set<String>>> CHILD_DIRECTION_KEYWORDS = new LinkedHashMap<>();

    static {
        addDirection(
                "Direction de la Réglementation et de la Facilitation",
                Set.of(
                        "reglementation",
                        "reglement",
                        "facilitation",
                        "procedure douaniere",
                        "procédure douanière",
                        "codification"),
                Map.of(
                        "Réglementation",
                        Set.of("reglementation", "reglement", "codification", "procedure douaniere"),
                        "Facilitation",
                        Set.of("facilitation", "guichet unique", "commerce exterieur")));

        addDirection(
                "Direction de la Lutte contre la Fraude",
                Set.of("fraude", "contrefacon", "contrefaçon", "blanchiment", "infraction", "saisie"),
                Map.of(
                        "Liaison et Renseignements",
                        Set.of("liaison", "renseignement", "information", "cooperation policiere"),
                        "Stratégies et Planification",
                        Set.of("strategie", "planification", "plan anti-fraude"),
                        "Audit a posteriori",
                        Set.of("audit a posteriori", "a posteriori", "controle a posteriori")));

        addDirection(
                "Direction du Tarif et des Règles d'Origine",
                Set.of("tarif", "nomenclature", "sh", "classification douaniere", "origine preferentielle"),
                Map.of(
                        "Tarif",
                        Set.of("tarif", "nomenclature", "sh ", " code sh"),
                        "Règles d'origine",
                        Set.of("regles d'origine", "origine", "cumulation", "protocole d'accord")));

        addDirection(
                "Direction de la Valeur",
                Set.of("valeur en douane", "valeur statistique", "evaluation douaniere", "methode de valeur"),
                Map.of(
                        "Évaluation",
                        Set.of("evaluation", "valeur en douane", "methode de valeur"),
                        "Recours et valeurs de base",
                        Set.of("recours", "valeurs de base", "contestation valeur")));

        addDirection(
                "Direction des Autres Produits d'Accises",
                Set.of("accise", "accises", "alcool", "tabac", "limonade", "boisson alcoolique"),
                Map.of(
                        "Alcools, Boissons alcooliques et Limonades",
                        Set.of("alcool", "boisson alcoolique", "limonade", "vin", "biere"),
                        "Tabacs et autres Produits d'Accises",
                        Set.of("tabac", "cigarette", "accise")));

        addDirection(
                "Direction des Huiles Minérales",
                Set.of("huile minerale", "carburant", "petrole", "gpl", "lubrifiant", "supercarburant"),
                Map.of(
                        "Producteurs", Set.of("producteur", "raffinerie", "importateur huile"),
                        "Distributeurs", Set.of("distributeur", "station service", "grossiste carburant")));

        addDirection(
                "Direction des Recettes du Trésor",
                Set.of("recette douaniere", "liquidation fiscale", "paiement douane", "tresor public"),
                Map.of(
                        "Recettes de Douanes",
                        Set.of("recette douane", "droits de douane", "encaissement douane"),
                        "Recettes des Accises",
                        Set.of("recette accise", "taxe accise"),
                        "Budget et Recettes Connexes",
                        Set.of("budget interne direction", "recette connexe", "fonds")));

        addDirection(
                "Direction des Ressources Humaines",
                Set.of(
                        "rh",
                        "ressources humaines",
                        "recrutement",
                        "formation",
                        "conge",
                        "paie agent",
                        "carriere",
                        "mutation"),
                Map.of(
                        "Recrutement et Formation",
                        Set.of("recrutement", "formation", "concours", "stage"),
                        "Administration",
                        Set.of("administration du personnel", "dossier administratif", "temps de travail"),
                        "OEuvres Sociales",
                        Set.of("oeuvres sociales", "oeuvre sociale", "mutuelle", "action sociale"),
                        "Relations Publiques et Protocole",
                        Set.of("relations publiques", "protocole", "communication interne", "ceremonie")));

        addDirection(
                "Direction des Équipements et de la Logistique",
                Set.of("logistique", "patrimoine", "imprimerie", "approvisionnement", "batiment", "mobilier"),
                Map.of(
                        "Gestion du Patrimoine",
                        Set.of("patrimoine", "batiment", "mobilier", "bureau physique"),
                        "Imprimerie et Approvisionnements",
                        Set.of("imprimerie service", "fourniture bureau", "approvisionnement", "carte de service")));

        addDirection(
                "Direction des Statistiques, Documentation et Études Économiques",
                Set.of("statistique", "documentation", "etude economique", "publication douaniere", "indicateur"),
                Map.of(
                        "Statistiques et Études Économiques",
                        Set.of("statistique", "etude economique", "indicateur", "macroeconomie"),
                        "Documentation",
                        Set.of("documentation", "archivage", "base documentaire")));

        addDirection(
                "Direction des Affaires Juridiques et Contentieuses",
                Set.of("contentieux", "juridique", "litige", "recours administratif", "conseil juridique"),
                Map.of(
                        "Affaires Contentieuses",
                        Set.of("contentieux", "litige", "tribunal", "recours"),
                        "Affaires Juridiques",
                        Set.of("juridique", "contrat", "conseil juridique")));

        addDirection(
                DSTI_TOP_NAME,
                Set.of(
                        "dsti",
                        "informatique",
                        "systeme d'information",
                        "ordinateur",
                        "logiciel",
                        "reseau",
                        "vpn",
                        "wifi",
                        "email",
                        "messagerie",
                        "compte utilisateur",
                        "mot de passe",
                        "active directory",
                        "portail",
                        "sydonia",
                        "asycuda"),
                Map.of(
                        "Développement et Maintenance des Applications",
                        SOFTWARE_CHILD_KEYWORDS,
                        "Réseaux, Télécommunications et Maintenance Hardware",
                        NETWORK_CHILD_KEYWORDS,
                        "Sydonia",
                        SYDONIA_CHILD_KEYWORDS));

        addDirection("Direction de l'Audit Interne", Set.of("audit interne", "controle interne"), Map.of());

        addDirection(
                "Direction des Finances Internes",
                Set.of("comptabilite", "tresorerie", "budget interne", "finance interne", "immobilisation"),
                Map.of(
                        "Comptabilité et Trésorerie",
                        Set.of("comptabilite", "tresorerie", "ecriture comptable", "rapprochement bancaire"),
                        "Budget Interne",
                        Set.of("budget interne", "enveloppe budgetaire", "suivi budgetaire")));

        addDirection(
                "Direction des Réformes et Modernisation",
                Set.of("reforme", "modernisation", "projet de reforme", "transformation organisationnelle"),
                Map.of());

        addDirection(
                "Bureau de Coordination",
                Set.of("coordination", "cellule de crise", "suivi interdirections"),
                Map.of());
    }

    private final SousDirectionRepository sousDirections;
    private final DirectionRepository directions;

    public record HandlingSuggestion(long sousDirectionId, long directionId) {}

    /**
     * Suggests handling direction + sous-direction. Preset may be empty: keywords in summary/description
     * (and preset-derived hints) still drive routing. {@link TicketIssuePresets#OTHER} is allowed.
     */
    public Optional<HandlingSuggestion> suggest(
            String issuePresetCode, String summary, String description) {
        String preset = issuePresetCode == null ? "" : issuePresetCode.trim();
        if (!preset.isEmpty()) {
            if (NO_AUTO_ROUTE_PRESETS.contains(preset)) {
                return Optional.empty();
            }
            if (!TicketIssuePresets.isAllowed(preset)) {
                return Optional.empty();
            }
        }
        String hay = normalizeHay(summary, description, preset);
        if (hay.isBlank()) {
            return Optional.empty();
        }
        Optional<RouteName> byPreset = preset.isEmpty() ? Optional.empty() : suggestFromPreset(preset, hay);
        Optional<RouteName> byKeywords = suggestFromKeywords(hay);
        RouteName choice = byPreset.or(() -> byKeywords).orElse(null);
        if (choice == null) {
            return Optional.empty();
        }
        return resolveRoute(choice.topDirection(), choice.childDirection());
    }

    private static boolean matchesDirectionLabel(String fullName, String childLabel) {
        if (fullName == null || childLabel == null) {
            return false;
        }
        String full = normalizeName(fullName);
        String child = normalizeName(childLabel);
        if (full.equals(child)) {
            return true;
        }
        if (full.endsWith(DGDA_SEPARATOR + child)) {
            return true;
        }
        return full.contains(" " + DGDA_SEPARATOR + " " + child);
    }

    private static String normalizeName(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.FRENCH).replace('\u2019', '\'').replaceAll("\\s+", " ").trim();
    }

    public boolean isValidPair(long sousDirectionId, long directionId) {
        return directions.findById(directionId).stream()
                .anyMatch(
                        d ->
                                d.getSousDirectionId() != null
                                        && d.getSousDirectionId().equals(sousDirectionId));
    }

    private static RoutingBranch resolveBranch(String preset, String hay) {
        if (NETWORK_PRESETS.contains(preset)) {
            return RoutingBranch.NETWORK;
        }
        if (SOFTWARE_PRESETS.contains(preset)) {
            return RoutingBranch.SOFTWARE;
        }
        boolean netKw = containsNetworkKeyword(hay);
        boolean softKw = containsSoftwareKeyword(hay);
        if (netKw && softKw) {
            int netScore = keywordStrengthNetwork(hay);
            int softScore = keywordStrengthSoftware(hay);
            return softScore > netScore ? RoutingBranch.SOFTWARE : RoutingBranch.NETWORK;
        }
        if (netKw) {
            return RoutingBranch.NETWORK;
        }
        if (softKw) {
            return RoutingBranch.SOFTWARE;
        }
        return RoutingBranch.SOFTWARE;
    }

    private Optional<RouteName> suggestFromPreset(String preset, String hay) {
        if (NETWORK_PRESETS.contains(preset) || SOFTWARE_PRESETS.contains(preset)) {
            RoutingBranch branch = resolveBranch(preset, hay);
            if (shouldPreferSydonia(hay)) {
                return Optional.of(new RouteName(DSTI_TOP_NAME, "Sydonia"));
            }
            return Optional.of(
                    new RouteName(
                            DSTI_TOP_NAME,
                            branch == RoutingBranch.NETWORK ? NETWORK_DIRECTION_NAME : SOFTWARE_DIRECTION_NAME));
        }
        if (GENERIC_IT_PRESETS.contains(preset)) {
            return Optional.of(new RouteName(DSTI_TOP_NAME, bestDstiChild(hay)));
        }
        if ("SECURITY_INCIDENT".equals(preset)) {
            return Optional.of(new RouteName("Direction de la Lutte contre la Fraude", null));
        }
        if ("TRAINING_REQUEST".equals(preset)) {
            return Optional.of(new RouteName("Direction des Ressources Humaines", null));
        }
        return Optional.empty();
    }

    /** Strong Sydonia / customs-data signal even when preset is network/software. */
    private static boolean shouldPreferSydonia(String hay) {
        return sydoniaSignalScore(hay) >= 4;
    }

    private static int sydoniaSignalScore(String hay) {
        int s = weightedKeywordScore(hay, SYDONIA_CHILD_KEYWORDS);
        if (DONNEES_FRAGMENT.matcher(hay).find()) {
            s += 3;
        }
        return s;
    }

    private static String bestDstiChild(String hay) {
        int syd = sydoniaSignalScore(hay);
        int net = weightedKeywordScore(hay, NETWORK_CHILD_KEYWORDS);
        int soft = weightedKeywordScore(hay, SOFTWARE_CHILD_KEYWORDS);
        int max = Math.max(syd, Math.max(net, soft));
        if (max == 0) {
            RoutingBranch b = resolveBranch("", hay);
            return b == RoutingBranch.NETWORK ? NETWORK_DIRECTION_NAME : SOFTWARE_DIRECTION_NAME;
        }
        boolean sydTop = syd == max;
        boolean netTop = net == max;
        boolean softTop = soft == max;
        if (sydTop && !netTop && !softTop) {
            return "Sydonia";
        }
        if (netTop && !sydTop && !softTop) {
            return NETWORK_DIRECTION_NAME;
        }
        if (softTop && !sydTop && !netTop) {
            return SOFTWARE_DIRECTION_NAME;
        }
        if (sydTop && (netTop || softTop)) {
            if (hay.contains("sydonia")
                    || hay.contains("asycuda")
                    || DONNEES_FRAGMENT.matcher(hay).find()
                    || hay.contains("manifeste")
                    || hay.contains("declaration")) {
                return "Sydonia";
            }
        }
        if (netTop && softTop) {
            RoutingBranch b = resolveBranch("", hay);
            return b == RoutingBranch.NETWORK ? NETWORK_DIRECTION_NAME : SOFTWARE_DIRECTION_NAME;
        }
        RoutingBranch b = resolveBranch("", hay);
        return b == RoutingBranch.NETWORK ? NETWORK_DIRECTION_NAME : SOFTWARE_DIRECTION_NAME;
    }

    private Optional<RouteName> suggestFromKeywords(String hay) {
        if (hay == null || hay.isBlank()) {
            return Optional.empty();
        }
        String top = bestTopDirection(hay);
        if (top == null) {
            return Optional.empty();
        }
        String child = bestChildDirection(top, hay);
        return Optional.of(new RouteName(top, child));
    }

    private String bestTopDirection(String hay) {
        int bestScore = 0;
        String best = null;
        for (var e : TOP_DIRECTION_KEYWORDS.entrySet()) {
            int score = weightedKeywordScore(hay, e.getValue());
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    private String bestChildDirection(String topDirection, String hay) {
        if (normalizeName(topDirection).equals(normalizeName(DSTI_TOP_NAME))) {
            return bestDstiChild(hay);
        }
        Map<String, Set<String>> childMap = CHILD_DIRECTION_KEYWORDS.get(topDirection);
        if (childMap == null || childMap.isEmpty()) {
            return null;
        }
        int bestScore = 0;
        String best = null;
        for (var e : childMap.entrySet()) {
            int score = weightedKeywordScore(hay, e.getValue());
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * Scores keyword hits; multi-word phrases and long tokens count more; very short tokens use padded word
     * boundaries to limit false positives.
     */
    private static int weightedKeywordScore(String hay, Set<String> keywords) {
        if (hay == null || hay.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String padded = " " + hay + " ";
        int score = 0;
        for (String raw : keywords) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String k = raw.trim().toLowerCase(Locale.FRENCH);
            k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
            if (k.contains(" ")) {
                if (hay.contains(k)) {
                    score += 4;
                }
            } else if (k.length() <= 4) {
                if (padded.contains(" " + k + " ")) {
                    score += 3;
                }
            } else if (hay.contains(k)) {
                score += 2;
            }
        }
        return score;
    }

    private static void addDirection(
            String topDirectionName, Set<String> topKeywords, Map<String, Set<String>> childKeywordMap) {
        Set<String> mergedTop = new LinkedHashSet<>(keywordsFromLabel(topDirectionName));
        mergedTop.addAll(topKeywords);
        TOP_DIRECTION_KEYWORDS.put(topDirectionName, mergedTop);

        Map<String, Set<String>> childMap = new LinkedHashMap<>();
        for (var e : childKeywordMap.entrySet()) {
            Set<String> childKeywords = new LinkedHashSet<>(keywordsFromLabel(e.getKey()));
            childKeywords.addAll(e.getValue());
            childMap.put(e.getKey(), childKeywords);
        }
        CHILD_DIRECTION_KEYWORDS.put(topDirectionName, childMap);
    }

    private static Set<String> keywordsFromLabel(String label) {
        String n = normalizeName(label)
                .replace("direction", " ")
                .replace("sous direction", " ")
                .replace("bureau", " ")
                .replace("des", " ")
                .replace("de", " ")
                .replace("du", " ")
                .replace("d'", " ")
                .replace("et", " ")
                .replace(",", " ");
        Set<String> out = new LinkedHashSet<>();
        out.add(normalizeName(label));
        for (String part : n.split("\\s+")) {
            if (part.length() >= 4) {
                out.add(part);
            }
        }
        return out;
    }

    private Optional<HandlingSuggestion> resolveRoute(String topDirectionName, String childDirectionName) {
        var sd = sousDirections.findAll().stream()
                .filter(s -> normalizeName(s.getName()).equals(normalizeName(topDirectionName)))
                .findFirst();
        if (sd.isEmpty()) {
            return Optional.empty();
        }
        long sdId = sd.get().getId();
        List<com.sysco.web.domain.Direction> childRows = directions.findAllBySousDirectionIdOrderByNameAsc(sdId);
        if (childRows.isEmpty()) {
            return Optional.empty();
        }
        com.sysco.web.domain.Direction chosen = null;
        if (childDirectionName != null && !childDirectionName.isBlank()) {
            chosen = childRows.stream()
                    .filter(d -> matchesDirectionLabel(d.getName(), childDirectionName))
                    .findFirst()
                    .orElse(null);
        }
        if (chosen == null) {
            chosen = childRows.stream()
                    .filter(d -> matchesDirectionLabel(d.getName(), ENSEMBLE_DIRECTION_NAME))
                    .findFirst()
                    .orElse(childRows.get(0));
        }
        return Optional.of(new HandlingSuggestion(sdId, chosen.getId()));
    }

    private enum RoutingBranch {
        NETWORK,
        SOFTWARE
    }

    private static String normalizeHay(String summary, String description, String presetCode) {
        String s = nz(summary) + " " + nz(description) + " " + presetRoutingContext(presetCode);
        if (s.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.FRENCH).replace('\u2019', '\'').replaceAll("\\s+", " ").trim();
    }

    private static String presetRoutingContext(String presetCode) {
        if (presetCode == null || presetCode.isBlank()) {
            return "";
        }
        return switch (presetCode.trim()) {
            case "NETWORK_WIFI" -> "wifi reseau sans fil couverture";
            case "NETWORK_VPN" -> "vpn acces distant reseau";
            case "PRINT_SCAN" -> "imprimante scanner copieur";
            case "HARDWARE_PC" -> "ordinateur pc portable materiel poste";
            case "HARDWARE_PHONE" -> "telephone mobile gsm smartphone";
            case "SOFTWARE_INSTALL" -> "installation logiciel mise a jour programme";
            case "SOFTWARE_BUG" -> "bug logiciel plantage application";
            case "DATA_SHARE_ACCESS" -> "partage donnees fichier acces dossier";
            case "PORTAL_WEB" -> "portail web site intranet";
            case "ACCOUNTS_ACCESS" -> "compte utilisateur acces profil authentification";
            case "PASSWORD_RESET" -> "mot de passe password reinitialisation compte";
            case "EMAIL_CALENDAR" -> "email messagerie outlook calendrier";
            case "SECURITY_INCIDENT" -> "incident securite phishing malware";
            case "TRAINING_REQUEST" -> "formation apprentissage stage";
            case "FACILITY_OFFICE" -> "bureau mobilier local physique";
            default -> "";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean containsNetworkKeyword(String hay) {
        if (hay.isEmpty()) {
            return false;
        }
        return hay.contains("reseau")
                || hay.contains("réseau")
                || hay.contains("network")
                || hay.contains("wifi")
                || hay.contains("wi-fi")
                || hay.contains("ethernet")
                || hay.contains("vpn")
                || hay.contains("lan ")
                || hay.contains(" dns")
                || hay.contains("firewall")
                || hay.contains("pare-feu")
                || hay.contains("pare feu")
                || hay.contains("imprim")
                || hay.contains("scan")
                || hay.contains("scanner")
                || hay.contains("telephon")
                || hay.contains("téléphon")
                || hay.contains("mobile")
                || hay.contains("gsm")
                || hay.contains("routeur")
                || hay.contains("router")
                || hay.contains("switch")
                || hay.contains("fibre")
                || hay.contains("cable r")
                || hay.contains("hardware")
                || hay.contains("materiel")
                || hay.contains("matériel")
                || hay.contains("laptop")
                || hay.contains("portable")
                || hay.contains("ordinateur")
                || hay.contains(" poste ")
                || hay.contains(" pc")
                || hay.contains("pc ")
                || hay.contains("pc issue")
                || hay.contains("probleme pc");
    }

    private static boolean containsSoftwareKeyword(String hay) {
        if (hay.isEmpty()) {
            return false;
        }
        return hay.contains("logiciel")
                || hay.contains("software")
                || hay.contains("application")
                || hay.contains("appli ")
                || hay.contains("programme")
                || hay.contains("bug")
                || hay.contains("plant")
                || hay.contains("crash")
                || hay.contains("install")
                || hay.contains("mise a jour")
                || hay.contains("mise à jour")
                || hay.contains("patch")
                || hay.contains("version")
                || hay.contains("executable")
                || hay.contains("script")
                || hay.contains("java")
                || hay.contains("windows update");
    }

    private static int keywordStrengthNetwork(String hay) {
        int n = 0;
        String[] keys = {
            "reseau", "réseau", "network", "wifi", "vpn", "imprim", "scan", "hardware", "portable", "telephon", "pc issue"
        };
        for (String k : keys) {
            if (hay.contains(k)) {
                n++;
            }
        }
        return n;
    }

    private static int keywordStrengthSoftware(String hay) {
        int n = 0;
        String[] keys = {"logiciel", "software", "install", "bug", "application", "patch", "mise a jour", "mise à jour"};
        for (String k : keys) {
            if (hay.contains(k)) {
                n++;
            }
        }
        return n;
    }

    private record RouteName(String topDirection, String childDirection) {}
}
