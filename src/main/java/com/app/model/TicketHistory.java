public class TicketHistory {

    private int id;
    private int ticketId;
    private String action;
    private String description;
    private String username;
    private String createdAt;

    public TicketHistory(int id, int ticketId, String action,
                         String description, String username, String createdAt) {

        this.id = id;
        this.ticketId = ticketId;
        this.action = action;
        this.description = description;
        this.username = username;
        this.createdAt = createdAt;
    }

    public String getDescription() { return description; }
    public String getUsername() { return username; }
    public String getCreatedAt() { return createdAt; }
}