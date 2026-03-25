package com.app.model;

import java.time.LocalDateTime;

public class LoginAudit {

    private String username;
    private LocalDateTime loginTime;

    public LoginAudit(String username, LocalDateTime loginTime) {
        this.username = username;
        this.loginTime = loginTime;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }
}
