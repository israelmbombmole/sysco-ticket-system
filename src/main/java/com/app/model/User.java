package com.app.model;

public class User {

    private int id;
    private String username;
    private String passwordHash;   // Used only for authentication
    private String role;
    private boolean active;
    private int ticketCount;
    private Integer sousDirectionId;   // FK (nullable)
    private String sousDirectionName;  // Display name (SDMA, SDRM...)
    private String email;
    private Integer directionId;
    private boolean hidden;
    private boolean mustChangePassword;

    // =====================================================
    // EMPTY CONSTRUCTOR (REQUIRED FOR DAO)
    // =====================================================
    public User() {
    }

    // =====================================================
    // LOGIN CONSTRUCTOR (FULL)
    // =====================================================
    public User(int id,
                String username,
                String passwordHash,
                String role,
                boolean active) {

        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    
    // =====================================================
// UI CONSTRUCTOR (WITH SOUS DIRECTION NAME)
// =====================================================
public User(int id,
            String username,
            String role,
            boolean active,
            String sousDirectionName) {

    this.id = id;
    this.username = username;
    this.role = role;
    this.active = active;
    this.sousDirectionName = sousDirectionName;
}
    // =====================================================
    // TABLE CONSTRUCTOR (NO PASSWORD)
    // =====================================================
    public User(int id,
                String username,
                String role,
                boolean active,
                Integer sousDirectionId) {

        this.id = id;
        this.username = username;
        this.role = role;
        this.active = active;
        this.sousDirectionId = sousDirectionId;
    }

    // =====================================================
    // GETTERS
    // =====================================================
    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Integer getSousDirectionId() {
        return sousDirectionId;
    }

    public String getSousDirectionName() {
        return sousDirectionName;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    // =====================================================
    // SETTERS
    // =====================================================
    public void setId(int id) {   // ← THIS WAS MISSING
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSousDirectionId(Integer sousDirectionId) {
        this.sousDirectionId = sousDirectionId;
    }

    public void setSousDirectionName(String sousDirectionName) {
        this.sousDirectionName = sousDirectionName;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

   @Override
public String toString() {
    return username + " - " + role +
           (sousDirectionName != null ? " - " + sousDirectionName : "");
    
}

private String directionName;
private boolean superAdmin;

public String getDirectionName() {
    return directionName;
}

public void setDirectionName(String directionName) {
    this.directionName = directionName;
}

public boolean isSuperAdmin() {
    return superAdmin;
}

public void setSuperAdmin(boolean superAdmin) {
    this.superAdmin = superAdmin;
}

private String sousDirection;

public String getSousDirection() {
    return sousDirection;
}

public void setSousDirection(String sousDirection) {
    this.sousDirection = sousDirection;
}


public String getEmail() { return email; }

public void setEmail(String email) { this.email = email; }

public Integer getDirectionId() {
    return directionId;
}

public void setDirectionId(Integer directionId) {
    this.directionId = directionId;
}

public boolean getHidden() {
    return hidden;
}

public void setHidden(boolean hidden) {
    this.hidden = hidden;
}
public boolean isMustChangePassword() {
    return mustChangePassword;
}

public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
}





}