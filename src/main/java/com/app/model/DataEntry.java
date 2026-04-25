package com.app.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DataEntry {

    private Integer ticketId;
    private Integer sousDirectionId;
    private final StringProperty dateEnregistrement = new SimpleStringProperty();
    private final StringProperty expediteur = new SimpleStringProperty();
    private final StringProperty objet = new SimpleStringProperty();
    private final StringProperty cotation = new SimpleStringProperty();
    private final StringProperty dateCotation = new SimpleStringProperty();
    private final StringProperty sousDirection = new SimpleStringProperty();

    public DataEntry(
            String dateEnregistrement,
            String expediteur,
            String objet,
            String cotation,
            String dateCotation,
            String sousDirection
    ) {
        this.dateEnregistrement.set(dateEnregistrement);
        this.expediteur.set(expediteur);
        this.objet.set(objet);
        this.cotation.set(cotation);
        this.dateCotation.set(dateCotation);
        this.sousDirection.set(sousDirection);
    }

    public DataEntry(
            String dateEnregistrement,
            String expediteur,
            String objet,
            String cotation,
            String dateCotation,
            String sousDirection,
            Integer ticketId,
            Integer sousDirectionId
    ) {
        this(dateEnregistrement, expediteur, objet, cotation, dateCotation, sousDirection);
        this.ticketId = ticketId;
        this.sousDirectionId = sousDirectionId;
    }

    public String getDateEnregistrement() { return dateEnregistrement.get(); }
    public String getExpediteur() { return expediteur.get(); }
    public String getObjet() { return objet.get(); }
    public String getCotation() { return cotation.get(); }
    public String getDateCotation() { return dateCotation.get(); }
    public String getSousDirection() { return sousDirection.get(); }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public Integer getSousDirectionId() {
        return sousDirectionId;
    }

    public void setSousDirectionId(Integer sousDirectionId) {
        this.sousDirectionId = sousDirectionId;
    }
}
