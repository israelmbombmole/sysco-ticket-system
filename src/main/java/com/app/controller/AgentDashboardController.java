package com.app.controller;

import com.app.auth.Session;
import com.app.dao.NotificationDAO;
import com.app.dao.TicketAssignmentDAO;
import com.app.dao.TicketDAO;
import com.app.model.Ticket;
import com.app.service.TicketService;
import com.app.dao.TicketTaskDAO;
import com.app.dao.UserDAO;
import com.app.model.TicketTask;
import com.app.model.User;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;
import javafx.stage.StageStyle;

public class AgentDashboardController {

    @FXML
private TableView<TicketTask> tableTickets;
    
@FXML private TableColumn<TicketTask, String> colResolution;

    @FXML private TableColumn<TicketTask, String> colTicketId;
    @FXML private TableColumn<TicketTask, String> colTitle;
    @FXML private TableColumn<TicketTask, String> colStatus;
    @FXML private TableColumn<TicketTask, String> colStarted;
    @FXML private TableColumn<TicketTask, String> colClosed;
    @FXML private Label lblTotal;
    @FXML private Label lblOpen;
    @FXML private Label lblClosed;
    @FXML private PieChart pieChart;
    @FXML private TextField txtSearch;
    @FXML private Label lblInProgress;
    @FXML private Label lblMerged;
    @FXML private LineChart<String, Number> performanceChart;
    @FXML private Label lblSlaBreaches;
    @FXML private Label lblNotifCount;
    
    
    
    
    
   @FXML private TableColumn<TicketTask, String> colType;

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        
        tableTickets.setRowFactory(tv -> {

    TableRow<TicketTask> row = new TableRow<>();

    ContextMenu menu = new ContextMenu();

    MenuItem view = new MenuItem(com.app.util.LanguageManager.getBundle().getString("viewDetails"));
    view.setOnAction(e -> openTaskDetails(row.getItem()));

    MenuItem reassign = new MenuItem(com.app.util.LanguageManager.getBundle().getString("reassignTaskTitle"));
    reassign.setOnAction(e -> reassignTask(row.getItem()));

    menu.getItems().addAll(view, reassign);

    row.setContextMenu(menu);
    

    return row;
});
        
        
 tableTickets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        int notifCount = NotificationDAO.getUnreadCount(Session.getUserId());
lblNotifCount.setText(String.valueOf(notifCount));
       int agentId = Session.getUserId();

int total = TicketDAO.countTicketsByUserAssignments(agentId);

int open =
        TicketDAO.countAssignedTicketsByStatus(agentId,"ASSIGNED")
      + TicketDAO.countAssignedTicketsByStatus(agentId,"ESCALATED");

int inProgress =
        TicketDAO.countAssignedTicketsByStatus(agentId,"IN_PROGRESS");

int merged =
        TicketDAO.countAssignedTicketsByStatus(agentId,"MERGED");

int closed =
        TicketDAO.countAssignedTicketsByStatus(agentId,"CLOSED");

lblTotal.setText(String.valueOf(total));
lblOpen.setText(String.valueOf(open));
lblInProgress.setText(String.valueOf(inProgress));
lblMerged.setText(String.valueOf(merged));
lblClosed.setText(String.valueOf(closed));

int breaches = TicketDAO.countAgentSlaBreaches(agentId);
lblSlaBreaches.setText(String.valueOf(breaches));

pieChart.getData().addAll(
        new PieChart.Data("Open", open),
        new PieChart.Data("In Progress", inProgress),
        new PieChart.Data("Merged", merged),
        new PieChart.Data("Closed", closed)
);
        
        

        colTicketId.setCellValueFactory(data ->
    new SimpleStringProperty("TSK-" + data.getValue().getId())
);

       colTitle.setCellValueFactory(data ->
    new SimpleStringProperty(data.getValue().getTitle()));

        colStatus.setCellValueFactory(data ->
    new SimpleStringProperty(data.getValue().getStatus()));

        
        
        colStarted.setCellValueFactory(data ->
    new SimpleStringProperty(
        data.getValue().getStartedAt() != null
            ? data.getValue().getStartedAt().format(formatter)
            : "-"
    )
);

colClosed.setCellValueFactory(data ->
    new SimpleStringProperty(
        data.getValue().getClosedAt() != null
            ? data.getValue().getClosedAt().format(formatter)
            : "-"
    )
);

colResolution.setCellValueFactory(data ->
    new SimpleStringProperty(
        data.getValue().getResolutionMinutes() != null
            ? data.getValue().getResolutionMinutes() + " min"
            : "-"
    )
);

      

        // ✅ Double-click to view details
        tableTickets.setRowFactory(tv -> {

    TableRow<TicketTask> row = new TableRow<>();

    row.setOnMouseClicked(event -> {
        if (event.getClickCount() == 2 && !row.isEmpty()) {
            openTaskDetails(row.getItem());
        }
    });

    return row;
});

        loadTasks();
        
        
        colType.setCellValueFactory(data ->
    new SimpleStringProperty(data.getValue().getTicketTitle()));
        
        colStatus.setCellFactory(column -> new TableCell<>() {

    @Override
    protected void updateItem(String status, boolean empty) {

        super.updateItem(status, empty);

        if (empty || status == null) {
            setText(null);
            setStyle("");
            return;
        }

        setText(status);

        switch (status) {

            case "PENDING":
                setStyle("-fx-background-color:#f39c12; -fx-text-fill:white;");
                break;

            case "IN_PROGRESS":
                setStyle("-fx-background-color:#3498db; -fx-text-fill:white;");
                break;

            case "COMPLETED":
                setStyle("-fx-background-color:#2ecc71; -fx-text-fill:white;");
                break;

            default:
                setStyle("");
        }
    }
});
        
        
        
        
        
        
        
        colType.setCellFactory(column -> new TableCell<>() {
    @Override
    protected void updateItem(String type, boolean empty) {
        super.updateItem(type, empty);

        if (empty || type == null) {
            setText(null);
            setStyle("");
            return;
        }

        setText(type);

        if (type.equalsIgnoreCase("EXTERNAL")) {
            setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
        } else {
            setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        }
    }
});
    
    }

  
    
    private void reassignTask(TicketTask task) {

    ChoiceDialog<User> dialog = new ChoiceDialog<>();
    dialog.setTitle(com.app.util.LanguageManager.getBundle().getString("reassignTaskTitle"));
    dialog.setHeaderText(com.app.util.LanguageManager.getBundle().getString("reassignTaskHeader"));

    dialog.getItems().addAll(UserDAO.getAllAgents());

    dialog.showAndWait().ifPresent(user -> {

        TicketTaskDAO.reassignTask(task.getId(), user.getId());

        loadTasks();
    });
}
    
    
    
    
    
    
   
   
    // ============================
    // START WORK
    // ============================
       
@FXML
private void handleStart() {

    TicketTask task = tableTickets.getSelectionModel().getSelectedItem();

    if (task == null) {
        showWarning(com.app.util.LanguageManager.getBundle().getString("errMustSelectTask"));
        return;
    }

    try {

        // ✅ CORRECT METHOD
        TicketTaskDAO.startTask(task.getId());

        // 🔔 notification
        NotificationDAO.create(
                Session.getUserId(),
                "Task Started",
                "You started task: " + task.getTitle(),
                "TASK"
        );

        // 🔄 refresh
        loadTasks();

        showInfo(com.app.util.LanguageManager.getBundle().getString("infoTaskStarted"));

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    
    private void showWarning(String message) {

    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle(com.app.util.LanguageManager.getBundle().getString("stageTicketSystem"));
    alert.setHeaderText(com.app.util.LanguageManager.getBundle().getString("dialogWarning"));
    alert.setContentText(message);

    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

    alert.showAndWait();
}
    
    
 @FXML
private void handleClose() {

    TicketTask task = tableTickets.getSelectionModel().getSelectedItem();

    if (task == null) {
        showError(com.app.util.LanguageManager.getBundle().getString("errMustSelectTask"));
        return;
    }

    try {

        // 🚨 IMPORTANT CHECK
        if (task.getStartedAt() == null) {
            showError(com.app.util.LanguageManager.getBundle().getString("errMustStartTaskBeforeClosing"));
            return;
        }

        TicketTaskDAO.completeTask(task.getId());

        loadTasks();

        showInfo(com.app.util.LanguageManager.getBundle().getString("infoTaskCompleted"));

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // ============================
    // OPEN DETAILS WINDOW
    // ============================

    private void openTicketDetails(Ticket ticket) {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/TicketDetails.fxml")
            );

            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));

            TicketDetailsController controller = loader.getController();
            controller.setTicket(ticket);

            stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageTicketDetails"));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
private void handleViewDetails() {

    TicketTask selectedTask = tableTickets.getSelectionModel().getSelectedItem();

    if (selectedTask == null) {
        System.out.println("⚠ No task selected");
        return;
    }

    try {
        // 🔥 Get the ticket from the task
        Ticket ticket = TicketDAO.getTicketById(selectedTask.getTicketId());

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TicketDetails.fxml"),
                ResourceBundle.getBundle("lang.messages")
        );

        Parent root = loader.load();

        TicketDetailsController controller = loader.getController();
        controller.setTicket(ticket);

        Stage stage = new Stage();
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageTicketDetails")
                + " - TCK-" + ticket.getId());

        stage.setScene(new Scene(root));
        stage.setMaximized(true);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
  
 private void showTicketDetails(Ticket ticket) {

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Ticket Details");
    alert.setHeaderText("Ticket ID: " + ticket.getId());

    String content =
            "Title: " + ticket.getTitle() + "\n\n" +
            "Status: " + ticket.getStatus() + "\n\n" +
            "Object / Instructions:\n" + ticket.getObject() + "\n\n" +
            "Started At: " + ticket.getStartedAt() + "\n\n" +
            "Closed At: " + ticket.getClosedAt() + "\n\n" +
            "Resolution Time: " + ticket.getResolutionMinutes() + " minutes";

    alert.setContentText(content);
    alert.getDialogPane().setPrefWidth(500); // wider popup
    alert.showAndWait();
}
 
 
 @FXML
private void openChat() {

    try {

        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/view/ChatPopup.fxml"));

        Scene scene = new Scene(loader.load());

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageChat"));
        stage.setWidth(450);
        stage.setHeight(600);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
     

@FXML
private void handleMerge() {
    showAlert(com.app.util.LanguageManager.getBundle().getString("errCannotMergeSelf"));
}  

private void showAlert(String msg) {

    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
}

private void showInfo(String msg) {

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
}
    
    
private void loadTasks() {

    try {

        int userId = Session.getUserId();

        ObservableList<TicketTask> tasks =
                TicketTaskDAO.getTasksForAgent(userId);

        // FILTER
        javafx.collections.transformation.FilteredList<TicketTask> filtered =
                new javafx.collections.transformation.FilteredList<>(tasks, p -> true);

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {

            filtered.setPredicate(task -> {

                if (newVal == null || newVal.isEmpty()) return true;

                String search = newVal.toLowerCase();

                if (String.valueOf(task.getId()).contains(search)) return true;
                if (task.getTitle() != null && task.getTitle().toLowerCase().contains(search)) return true;
                if (task.getStatus() != null && task.getStatus().toLowerCase().contains(search)) return true;
                if (task.getTicketTitle() != null && task.getTicketTitle().toLowerCase().contains(search)) return true;

                return false;
            });
        });

        javafx.collections.transformation.SortedList<TicketTask> sorted =
                new javafx.collections.transformation.SortedList<>(filtered);

        sorted.comparatorProperty().bind(tableTickets.comparatorProperty());

        tableTickets.setItems(sorted);

    } catch (Exception e) {

        e.printStackTrace();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error loading tasks");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}

private void loadPerformanceChart(){

    int agentId = Session.getUserId();

    Map<String,Integer> stats =
            TicketDAO.getAgentDailyPerformance(agentId);

    XYChart.Series<String,Number> series =
            new XYChart.Series<>();

    series.setName("Closed Tickets");

    for(String date : stats.keySet()){

        series.getData().add(
                new XYChart.Data<>(date, stats.get(date))
        );
    }

    performanceChart.getData().clear();
    performanceChart.getData().add(series);
}


private void openTaskDetails(TicketTask task) {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TaskDetails.fxml")
        );

        Parent root = loader.load();

        TaskDetailsController controller = loader.getController();
        controller.setTask(task);

        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageTaskDetails")
                + " - TSK-" + task.getId());
        stage.setWidth(600);
        stage.setHeight(500);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


@FXML
private void openNotifications() {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/NotificationPopup.fxml")
        );

        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageNotifications"));
        stage.setWidth(400);
        stage.setHeight(500);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


}
