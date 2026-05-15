package com.app.controller;

import com.app.auth.Session;
import com.app.dao.DataShareDAO;
import com.app.dao.TicketDAO;
import com.app.model.DataShareFile;
import com.app.model.Ticket;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.LanguageManager;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.stage.Stage;

import java.util.List;

public class UserActivityController {

    // ======================
    // TABLES
    // ======================

    @FXML private TableView<Ticket> tableMyTickets;
    @FXML private TableView<DataShareFile> tableMyFiles;
    

    // ======================
    // SEARCH FIELDS
    // ======================

    @FXML private TextField txtSearchTickets;
    @FXML private TextField txtSearchMyFiles;
   

    // ======================
    // TICKET COLUMNS
    // ======================

    @FXML private TableColumn<Ticket, String> colRef;
    @FXML private TableColumn<Ticket, String> colTitle;
    @FXML private TableColumn<Ticket, String> colStatus;
    @FXML private TableColumn<Ticket, Void> colView;
    @FXML private TableColumn<Ticket, Void> colEdit;

    // ======================
    // MY FILES COLUMNS
    // ======================

    @FXML private TableColumn<DataShareFile, String> colFileName;
    @FXML private TableColumn<DataShareFile, String> colRole;
    @FXML private TableColumn<DataShareFile, String> colDate;

    // ======================
    // RECEIVED FILES COLUMNS
    // ======================

    

  

    // ======================
    // FILTERED LISTS
    // ======================

    private FilteredList<Ticket> filteredTickets;
    private FilteredList<DataShareFile> filteredMyFiles;
    private FilteredList<DataShareFile> filteredReceivedFiles;

    // ======================
    // INITIALIZE
    // ======================

    
 
    
    @FXML
    public void initialize() {

        tableMyTickets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableMyFiles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // Ticket columns
        colRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.status(item));
            }
        });

        tableMyTickets.setPlaceholder(new Label(I18n.t("noContentInTable", "No content in table")));
        tableMyFiles.setPlaceholder(new Label(I18n.t("noContentInTable", "No content in table")));

        // Shared files
        colFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        // Received files
        

        setupViewButton();
        setupEditButton();

        loadMyTickets();
        loadMyFiles();
        
    }

    // ======================
    // VIEW BUTTON
    // ======================

    private void setupViewButton() {

        colView.setCellFactory(param -> new TableCell<>() {

            private final Button btn = new Button("👁");
            

            {
                btn.setOnAction(event -> {

                    Ticket ticket = getTableView().getItems().get(getIndex());

    if (ticket != null) {
        openTicketDetails(ticket);
    }

                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {

                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);

            }
        });
    }

    // ======================
    // EDIT BUTTON
    // ======================

    private void setupEditButton() {

    colEdit.setCellFactory(param -> new TableCell<>() {

        private final Button btn = new Button("✏");

        {
            btn.setOnAction(event -> {

                Ticket ticket = getTableView().getItems().get(getIndex());

                if (ticket != null) {
                    openEditTicket(ticket);
                }

            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {

            super.updateItem(item, empty);

            if (empty) {
                setGraphic(null);
                return;
            }

            Ticket ticket = getTableView().getItems().get(getIndex());

            if (ticket == null) {
                setGraphic(null);
                return;
            }

            // Disable edit if CLOSED
            btn.setDisable("CLOSED".equalsIgnoreCase(ticket.getStatus()));

            setGraphic(btn);
        }
    });
}

    // ======================
    // LOAD TICKETS
    // ======================

    private void loadMyTickets() {

        List<Ticket> tickets =
                TicketDAO.getTicketsCreatedBy(Session.getUserId());

        ObservableList<Ticket> list =
                FXCollections.observableArrayList(tickets);

        filteredTickets = new FilteredList<>(list, p -> true);

        tableMyTickets.setItems(filteredTickets);

        txtSearchTickets.textProperty().addListener((obs, oldVal, newVal) -> {

            filteredTickets.setPredicate(ticket -> {

    if (newVal == null || newVal.isEmpty()) {
        return true;
    }

    String keyword = newVal.toLowerCase();

    return (ticket.getTitle() != null && ticket.getTitle().toLowerCase().contains(keyword))
            || (ticket.getStatus() != null && ticket.getStatus().toLowerCase().contains(keyword))
            || (ticket.getReference() != null && ticket.getReference().toLowerCase().contains(keyword));
});
        });
    }

    // ======================
    // LOAD MY FILES
    // ======================

    private void loadMyFiles() {

        List<DataShareFile> files =
                DataShareDAO.getFilesSharedBy(Session.getUserId());

        ObservableList<DataShareFile> list =
                FXCollections.observableArrayList(files);

        filteredMyFiles = new FilteredList<>(list, p -> true);

        tableMyFiles.setItems(filteredMyFiles);

        txtSearchMyFiles.textProperty().addListener((obs, oldVal, newVal) -> {

            filteredMyFiles.setPredicate(file -> {

                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }

                String keyword = newVal.toLowerCase();

                return file.getFileName().toLowerCase().contains(keyword)
                        || file.getRole().toLowerCase().contains(keyword)
                        || file.getDate().toLowerCase().contains(keyword);
            });
        });
    }

    

    // ======================
    // OPEN TICKET DETAILS
    // ======================

  private void openTicketDetails(Ticket ticket) {

    try {

        FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/TicketDetails.fxml"),
        LanguageManager.getBundle()
);

Parent root = loader.load();

        TicketDetailsController controller = loader.getController();
        controller.setTicket(ticket);

        Stage stage = new Stage();
        stage.setTitle(I18n.t("ticketDetails", "Ticket Details") + " - TCK-" + ticket.getId());

        Scene scene = new Scene(root);
        AppUiStyles.applyToScene(scene);
        stage.setScene(scene);

        stage.setMaximized(true);
        stage.centerOnScreen();
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // ======================
    // OPEN EDIT TICKET
    // ======================

   private void openEditTicket(Ticket ticket) {

    try {

        FXMLLoader loader;
        Parent root;

        if ("EXTERNAL".equalsIgnoreCase(ticket.getTicketType())) {

            loader = new FXMLLoader(
                    getClass().getResource("/view/external_ticket_edit.fxml"),
                    LanguageManager.getBundle()
            );

            root = loader.load();

            ExternalTicketEditController controller = loader.getController();

            // Load full ticket (attachments etc.)
            Ticket fullTicket = TicketDAO.getTicketById(ticket.getId());
            controller.loadTicket(fullTicket);

        } else {

            loader = new FXMLLoader(
                    getClass().getResource("/view/ticket_create.fxml"),
                    LanguageManager.getBundle()
            );

            root = loader.load();

            TicketCreateController controller = loader.getController();
            controller.loadTicket(ticket);
        }

        Stage stage = new Stage();
        Scene editScene = new Scene(root);
        AppUiStyles.applyToScene(editScene);
        stage.setScene(editScene);
        stage.setTitle(I18n.t("button.edit", "Edit") + " " + I18n.t("ticket", "Ticket"));
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
}