package com.app.model;

public class Notification {

    private int id;
    private int userId;
    private String title;
    private String message;
    private String type;
    private int isRead;
    private String createdAt;
    private String targetType;
    private Integer targetId;
    private String targetRef;

    public Notification(int id,int userId,String title,String message,String type,int isRead,String createdAt,
                        String targetType, Integer targetId, String targetRef){
        this.id=id;
        this.userId=userId;
        this.title=title;
        this.message=message;
        this.type=type;
        this.isRead=isRead;
        this.createdAt=createdAt;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetRef = targetRef;
    }

    public int getId(){return id;}
    public int getUserId(){return userId;}
    public String getTitle(){return title;}
    public String getMessage(){return message;}
    public String getType(){return type;}
    public int getIsRead(){return isRead;}
    public String getCreatedAt(){return createdAt;}
    public String getTargetType(){return targetType;}
    public Integer getTargetId(){return targetId;}
    public String getTargetRef(){return targetRef;}
}