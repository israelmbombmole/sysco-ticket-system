package com.app.util;

import com.app.dao.TicketDAO;
import com.app.model.Ticket;

import com.app.controller.ExternalTicketEditController;
import com.app.controller.UserExcelEntryController;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TicketEditorRouter {

    public static void open(Ticket ticket) {

        try {

            // Always reload full ticket
            Ticket fullTicket = TicketDAO.getTicketById(ticket.getId());

            if (fullTicket == null) {
                System.out.println("Unable to load ticket.");
                return;
            }

            String ticketType = fullTicket.getTicketType();

            FXMLLoader loader;
            Parent root;

            // ================================
            // EXTERNAL TICKET EDITOR
            // ================================
            if ("EXTERNAL".equalsIgnoreCase(ticketType)) {

                loader = new FXMLLoader(
                        TicketEditorRouter.class.getResource("/view/external_ticket_edit.fxml"),
                        LanguageManager.getBundle()
                );

                root = loader.load();

                ExternalTicketEditController controller = loader.getController();
                controller.loadTicket(fullTicket);

            }

            // ================================
            // INTERNAL TICKET EDITOR
            // ================================
            else {

                loader = new FXMLLoader(
                        TicketEditorRouter.class.getResource("/view/user_excel_entry.fxml"),
                        LanguageManager.getBundle()
                );

                root = loader.load();

                UserExcelEntryController controller = loader.getController();
                controller.loadTicket(fullTicket);

            }

            Stage stage = new Stage();
            stage.setTitle("Edit Ticket - " + TicketUtil.formatTicketRef(fullTicket.getId()));
            Scene editScene = new Scene(root);
            AppUiStyles.applyToScene(editScene);
            stage.setScene(editScene);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

}