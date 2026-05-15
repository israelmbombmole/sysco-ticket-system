package com.sysco.web.bootstrap;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent seed for DGDA organisation tree: each DGDA <em>Direction</em> is stored as {@link SousDirection}; each DGDA
 * <em>sous-direction</em> (or “ensemble” when none) is stored as {@link Direction} linked to that parent.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class DgdaOrganizationSeedRunner implements CommandLineRunner {

    private final SousDirectionRepository sousDirections;
    private final DirectionRepository directions;

    private static final List<DgdaRow> ROWS =
            List.of(
                    new DgdaRow(
                            "Direction de la Réglementation et de la Facilitation",
                            List.of("Réglementation", "Facilitation")),
                    new DgdaRow(
                            "Direction de la Lutte contre la Fraude",
                            List.of(
                                    "Liaison et Renseignements",
                                    "Stratégies et Planification",
                                    "Audit a posteriori")),
                    new DgdaRow(
                            "Direction du Tarif et des Règles d'Origine",
                            List.of("Tarif", "Règles d'origine")),
                    new DgdaRow(
                            "Direction de la Valeur",
                            List.of("Évaluation", "Recours et valeurs de base")),
                    new DgdaRow(
                            "Direction des Autres Produits d'Accises",
                            List.of(
                                    "Alcools, Boissons alcooliques et Limonades",
                                    "Tabacs et autres Produits d'Accises")),
                    new DgdaRow(
                            "Direction des Huiles Minérales",
                            List.of("Producteurs", "Distributeurs")),
                    new DgdaRow(
                            "Direction des Recettes du Trésor",
                            List.of(
                                    "Recettes de Douanes",
                                    "Recettes des Accises",
                                    "Budget et Recettes Connexes")),
                    new DgdaRow(
                            "Direction des Ressources Humaines",
                            List.of(
                                    "Recrutement et Formation",
                                    "Administration",
                                    "OEuvres Sociales",
                                    "Relations Publiques et Protocole")),
                    new DgdaRow(
                            "Direction des Équipements et de la Logistique",
                            List.of("Gestion du Patrimoine", "Imprimerie et Approvisionnements")),
                    new DgdaRow(
                            "Direction des Statistiques, Documentation et Études Économiques",
                            List.of("Statistiques et Études Économiques", "Documentation")),
                    new DgdaRow(
                            "Direction des Affaires Juridiques et Contentieuses",
                            List.of("Affaires Contentieuses", "Affaires Juridiques")),
                    new DgdaRow(
                            "Direction des Systèmes et Technologies d'Information",
                            List.of(
                                    "Développement et Maintenance des Applications",
                                    "Réseaux, Télécommunications et Maintenance Hardware",
                                    "Sydonia")),
                    new DgdaRow("Direction de l'Audit Interne", List.of()),
                    new DgdaRow(
                            "Direction des Finances Internes",
                            List.of("Comptabilité et Trésorerie", "Budget Interne")),
                    new DgdaRow("Direction des Réformes et Modernisation", List.of()),
                    new DgdaRow("Bureau de Coordination", List.of()));

    @Override
    @Transactional
    public void run(String... args) {
        int addedSd = 0;
        int addedDir = 0;
        for (DgdaRow row : ROWS) {
            Optional<SousDirection> existingSd = sousDirections.findByNameIgnoreCase(row.parentName());
            SousDirection sd =
                    existingSd.orElseGet(
                            () -> {
                                SousDirection s = new SousDirection();
                                s.setName(row.parentName());
                                return sousDirections.save(s);
                            });
            if (existingSd.isEmpty()) {
                addedSd++;
            }

            if (row.childLabels().isEmpty()) {
                String fullName = ensembleDirectionName(row.parentName());
                if (!directions.existsByNameIgnoreCase(fullName)) {
                    directions.save(direction(fullName, sd.getId()));
                    addedDir++;
                }
            } else {
                for (String child : row.childLabels()) {
                    String fullName = row.parentName() + " — " + child;
                    if (!directions.existsByNameIgnoreCase(fullName)) {
                        directions.save(direction(fullName, sd.getId()));
                        addedDir++;
                    }
                }
            }
        }
        log.info(
                "DGDA organisation seed checked ({} DGDA directions defined). This run: +{} sous_direction rows, +{} direction rows (totals: sous_directions={}, directions={}).",
                ROWS.size(),
                addedSd,
                addedDir,
                sousDirections.count(),
                directions.count());
    }

    private static String ensembleDirectionName(String parentName) {
        return parentName + " — Ensemble direction";
    }

    private static Direction direction(String name, long sousDirectionId) {
        Direction d = new Direction();
        d.setName(name);
        d.setSousDirectionId(sousDirectionId);
        return d;
    }

    private record DgdaRow(String parentName, List<String> childLabels) {}
}
