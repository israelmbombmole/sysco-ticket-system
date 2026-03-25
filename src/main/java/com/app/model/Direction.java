package com.app.model;

public class Direction {

    private int id;
    private String name;

    public Direction(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name; // Important for ComboBox display
    }
}