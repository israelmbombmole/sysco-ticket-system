package com.app.util;

import java.io.File;
import java.util.List;
import java.util.Optional;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Modal workflow: complete official mission order (ordre de mission) before persisting the mission.
 */
public final class MissionOrdreDialog {

    private MissionOrdreDialog() {}

    /**
     * @param pendingOrderAttachmentFiles non-empty only when {@code missionId == 0}; uploaded after insert.
     */
    public record Result(
            String orderReference,
            String orderIssueDate,
            String orderIssuedBy,
            String orderBody,
            List<File> pendingOrderAttachmentFiles
    ) {
        public Result {
            pendingOrderAttachmentFiles = pendingOrderAttachmentFiles != null
                    ? List.copyOf(pendingOrderAttachmentFiles)
                    : List.of();
        }
    }

    public static Optional<Result> show(Window owner, int missionId) {
        return showInternal(owner, missionId, false);
    }

    /**
     * Read-only ordre viewer (e.g. from mission overview). No persistence; uses the same layout as save flow.
     */
    public static void showViewOnly(Window owner, int missionId) {
        if (missionId <= 0) {
            return;
        }
        showInternal(owner, missionId, true);
    }

    private static Optional<Result> showInternal(Window owner, int missionId, boolean viewOnly) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MissionOrdreDialog.class.getResource("/view/mission_ordre_dialog.fxml"),
                    LanguageManager.getBundle());
            Parent root = loader.load();
            MissionOrdreDialogController c = loader.getController();

            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle(viewOnly
                    ? I18n.t("missionOrdreViewTitle", "Official mission order")
                    : I18n.t("missionOrdreWindowTitle", "Mission order"));
            stage.setScene(new Scene(root));
            AppUiStyles.applyToScene(stage.getScene());

            c.prepare(stage, missionId, viewOnly);
            stage.showAndWait();
            return viewOnly ? Optional.empty() : c.getOutcome();
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
