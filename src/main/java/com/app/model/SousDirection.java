package com.app.model;

public class SousDirection {

    private int id;
    private String name;

    public SousDirection(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public SousDirection(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name; 
    }
}
