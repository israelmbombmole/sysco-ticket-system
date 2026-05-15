package com.app.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** One line in the journey timeline. */
public class CourierJourneyLine {
    private final StringProperty atTime = new SimpleStringProperty();
    private final StringProperty text = new SimpleStringProperty();

    public CourierJourneyLine(String atTime, String text) {
        this.atTime.set(atTime != null ? atTime : "");
        this.text.set(text != null ? text : "");
    }
    public String getAtTime() { return atTime.get(); }
    public String getText() { return text.get(); }
    public StringProperty atTimeProperty() { return atTime; }
    public StringProperty textProperty() { return text; }
}
