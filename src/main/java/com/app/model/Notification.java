package com.app.model;

public class Notification {

    private int id;
    private int userId;
    private String title;
    private String message;
    private String type;
    private int isRead;
    private String createdAt;

    public Notification(int id,int userId,String title,String message,String type,int isRead,String createdAt){
        this.id=id;
        this.userId=userId;
        this.title=title;
        this.message=message;
        this.type=type;
        this.isRead=isRead;
        this.createdAt=createdAt;
    }

    public int getId(){return id;}
    public int getUserId(){return userId;}
    public String getTitle(){return title;}
    public String getMessage(){return message;}
    public String getType(){return type;}
    public int getIsRead(){return isRead;}
    public String getCreatedAt(){return createdAt;}
}