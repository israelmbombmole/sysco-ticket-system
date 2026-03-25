package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.model.TicketTask;
import com.app.model.User;
import java.time.LocalDateTime;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.List;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class MyWorkController {

    // ================= TABLES =================
    @FXML private TableView<Ticket> tableTickets;
    @FXML private TableView<TicketTask> tableTasks;

    // ================= TICKET COLUMNS =================
    @FXML private TableColumn<Ticket, Integer> colId;
    @FXML private TableColumn<Ticket, String> colTitle;
    @FXML private TableColumn<Ticket, String> colStatus;
    @FXML private TableColumn<Ticket, String> colPriority;
    @FXML private TableColumn<Ticket, String> colStart;
    @FXML private TableColumn<Ticket, String> colClose;
    @FXML private TableColumn<Ticket, String> colResolution;
    @FXML private TableColumn<Ticket, Void> colAction;

    // ================= TASK COLUMNS =================
    @FXML private TableColumn<TicketTask, Integer> colTaskId;
    @FXML private TableColumn<TicketTask, String> colTaskTitle;
    @FXML private TableColumn<TicketTask, String> colTaskStatus;
    @FXML private TableColumn<TicketTask, String> colTaskStart;
    @FXML private TableColumn<TicketTask, LocalDateTime> colTaskClose;
    @FXML private TableColumn<TicketTask, String> colTaskResolution;
    @FXML private TableColumn<TicketTask, Void> colTaskAction;

    // ================= INIT =================
    @FXML
    public void initialize() {

        setupTicketColumns();
        setupTaskColumns();
         
        loadTickets();
        loadTasks();

        setupTaskDoubleClick();
    }

    // ================= LOAD =================

    private void loadTickets() {

    int userId = Session.getUserId();
    String role = Session.getRole();

    if ("ADMIN".equalsIgnoreCase(role)) {
        tableTickets.setItems(TicketDAO.getAssignedTickets());
    } else {
        tableTickets.setItems(TicketDAO.getAssignedTicketsByUser(userId));
    }

    addTicketButtons();
}

    private void loadTasks() {

        int userId = Session.getUserId();

        tableTasks.setItems(
                TicketTaskDAO.getTasksForAgent(userId)
        );

        addTaskButtons();
    }

    // ================= COLUMN SETUP =================

    private void setupTicketColumns() {

        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colPriority.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPriority()));

        colStart.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getStartedAt() != null ?
                                c.getValue().getStartedAt().toString() : "-"
                )
        );

        colClose.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getClosedAt() != null ?
                                c.getValue().getClosedAt().toString() : "-"
                )
        );

        colTaskResolution.setCellValueFactory(c -> {

    Integer duration = c.getValue().getDurationMinutes();

    if (duration == null || duration <= 0) {
        return new SimpleStringProperty("-");
    }

    return new SimpleStringProperty(duration + (duration == 1 ? " min" : " mins"));
});
    }

    
    
    
    private void setupTaskColumns() {

    colTaskId.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getId()).asObject());

    colTaskTitle.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getTitle()));

    colTaskStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStatus()));

    // ================= START =================
    colTaskStart.setCellValueFactory(c ->
            new SimpleStringProperty(
                    c.getValue().getStartedAt() != null
                            ? c.getValue().getStartedAt().toString().replace("T", " ")
                            : "-"
            )
    );

    // ================= CLOSE (🔥 FIXED) =================
    colTaskClose.setCellValueFactory(c ->
            new SimpleObjectProperty<>(c.getValue().getClosedAt())
    );

    colTaskClose.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(LocalDateTime item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText("-");
            } else {
                setText(item.toString().replace("T", " "));
            }
        }
    });

    // ================= DURATION =================
    colTaskResolution.setCellValueFactory(c ->
    new SimpleStringProperty(
        (c.getValue().getDurationMinutes() != null &&
         c.getValue().getDurationMinutes() > 0)
            ? c.getValue().getDurationMinutes() + " min"
            : "-"
    )
);
}

    // ================= BUTTONS =================

    private void addTicketButtons() {

    colAction.setPrefWidth(300); // 🔥 IMPORTANT FIX

    colAction.setCellFactory(param -> new TableCell<>() {

        private final Button view = new Button("View");
        private final Button start = new Button("Start");
        private final Button close = new Button("Close");
        private final Button escalate = new Button("Escalate");

        {
            // 🎨 STYLE
            view.setStyle("-fx-background-color:#2563eb; -fx-text-fill:white; -fx-font-size:11;");
            start.setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white; -fx-font-size:11;");
            close.setStyle("-fx-background-color:#10b981; -fx-text-fill:white; -fx-font-size:11;");
            escalate.setStyle("-fx-background-color:#ef4444; -fx-text-fill:white; -fx-font-size:11;");

            // 🔥 FORCE WIDTH (VERY IMPORTANT)
            view.setMinWidth(60);
            start.setMinWidth(60);
            close.setMinWidth(60);
            escalate.setMinWidth(75);

            // ================= VIEW =================
            view.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                openTicket(t);
            });

            // ================= START =================
            start.setOnAction(e -> {
    Ticket t = getTableView().getItems().get(getIndex());

    TicketDAO.startTicket(t.getId());

    // 🔥 FORCE FULL RELOAD
    tableTickets.getItems().clear();
    loadTickets();
});

            // ================= CLOSE =================
            close.setOnAction(e -> {
    Ticket t = getTableView().getItems().get(getIndex());

    TicketDAO.closeTicket(t.getId());

    tableTickets.getItems().clear();
    loadTickets();
});

            // ================= ESCALATE =================
            escalate.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                escalateTicket(t);
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {

            if (empty) {
                setGraphic(null);
                return;
            }

            Ticket t = getTableView().getItems().get(getIndex());

            // 🔥 SMART ENABLE / DISABLE
            start.setDisable(!"ASSIGNED".equalsIgnoreCase(t.getStatus()));
            close.setDisable(!"IN_PROGRESS".equalsIgnoreCase(t.getStatus()));
            escalate.setDisable("CLOSED".equalsIgnoreCase(t.getStatus()));

            HBox box = new HBox(6, view, start, close, escalate);
            box.setStyle("-fx-alignment:center;");

            setGraphic(box);
        }
    });
}

    
    private void escalateTicket(Ticket ticket) {

    try {

        List<User> users = UserDAO.getAssignableUsers();

        if (users.isEmpty()) return;

        ChoiceDialog<User> dialog =
                new ChoiceDialog<>(users.get(0), users);

        dialog.setTitle("Escalate Ticket");
        dialog.setHeaderText("Select user to escalate to");

        dialog.showAndWait().ifPresent(user -> {

            TicketDAO.escalateTicket(
                    ticket.getId(),
                    Session.getUserId(),
                    user.getId(),
                    "Escalated from MyWork"
            );

            refresh();
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
   private void addTaskButtons() {

    colTaskAction.setPrefWidth(220);

    colTaskAction.setCellFactory(param -> new TableCell<>() {

        private final Button start = new Button("Start");
        private final Button close = new Button("Close");
        private final Button reassign = new Button("Reassign");

        {
            start.setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white; -fx-font-size:11;");
            close.setStyle("-fx-background-color:#10b981; -fx-text-fill:white; -fx-font-size:11;");
            reassign.setStyle("-fx-background-color:#6b7280; -fx-text-fill:white; -fx-font-size:11;");

            start.setMinWidth(60);
            close.setMinWidth(60);
            reassign.setMinWidth(80);

            start.setOnAction(e -> {
                TicketTask t = getTableView().getItems().get(getIndex());
                TicketTaskDAO.startTask(t.getId());
                refresh();
            });

            close.setOnAction(e -> {
    TicketTask t = getTableView().getItems().get(getIndex());

    TicketTaskDAO.closeTask(t.getId()); // ✅ CORRECT METHOD

    refresh();
});
            reassign.setOnAction(e -> {
                TicketTask t = getTableView().getItems().get(getIndex());
                reassignTask(t);
            });
        }

        @Override
protected void updateItem(Void item, boolean empty) {

    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
        setGraphic(null);
        return;
    }

    TicketTask t = (TicketTask) getTableRow().getItem();

    int currentUserId = Session.getUserId();
    boolean isMine = t.getAssignedTo() == currentUserId;

    String status = t.getStatus();
    if (status == null) status = "PENDING";

    // ✅ CORRECT LOGIC
    start.setDisable(!(isMine && status.equalsIgnoreCase("PENDING")));
    close.setDisable(!(isMine && status.equalsIgnoreCase("IN_PROGRESS")));
    reassign.setDisable(!isMine);

    HBox box = new HBox(6, start, close, reassign);
    box.setStyle("-fx-alignment:center;");

    setGraphic(box);
}
    });
}

    // ================= ACTIONS =================

  private void openTicket(Ticket t) {

    try {

        MainController.loadPage("TicketDetails.fxml", c -> {

            if (c instanceof TicketDetailsController controller) {
                controller.setTicket(t);
            }

        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}

  

    private void reassignTask(TicketTask task) {

        List<User> users = UserDAO.getAssignableUsers();

        if (users.isEmpty()) return;

        ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
        dialog.setTitle("Reassign Task");
        dialog.setHeaderText("Select user");

        dialog.showAndWait().ifPresent(user -> {
            TicketTaskDAO.reassignTask(task.getId(), user.getId());
            refresh();
        });
    }

    

    // ================= DOUBLE CLICK =================

    private void setupTaskDoubleClick() {

    tableTasks.setRowFactory(tv -> {
        TableRow<TicketTask> row = new TableRow<>();

        row.setOnMouseClicked(event -> {

            if (!row.isEmpty() && event.getClickCount() == 2) {

                TicketTask task = row.getItem();

                System.out.println("DOUBLE CLICK TASK → " + task.getId());

                openTaskDetails(task); // ✅ CORRECT
            }
        });

        return row;
    });
}
  
    private void openTaskDetails(TicketTask task) {

    System.out.println("DOUBLE CLICK TASK → " + task.getId());

    MainController.loadPage("TaskDetails.fxml", controller -> {

        if (controller instanceof TaskDetailsController tc) {
            tc.setTask(task);
        }

    });
}
    
    
    private void refresh() {
    tableTickets.getItems().clear();
    tableTasks.getItems().clear();

    loadTickets();
    loadTasks();
}
    
   
    
 
    private void openReassignPopup(TicketTask task) {

    try {

        List<User> users = UserDAO.getAssignableUsers();

        if (users.isEmpty()) return;

        ChoiceDialog<User> dialog =
                new ChoiceDialog<>(users.get(0), users);

        dialog.setTitle("Reassign Task");
        dialog.setHeaderText("Select new user");

        dialog.showAndWait().ifPresent(selected -> {

            TicketTaskDAO.reassignTask(task.getId(), selected.getId());

            refresh();
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    
}