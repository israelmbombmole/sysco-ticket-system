package com.app.model;

public class Department {

    private int id;
    private String name;

    // ✅ default constructor (ADD THIS)
    public Department() {
    }

    // existing constructor
    public Department(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    // ✅ also add setters (recommended)
    public void setId(int id) { this.id = id; }

    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return name;
    }
}