package com.app.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DataEntry {

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

    public String getDateEnregistrement() { return dateEnregistrement.get(); }
    public String getExpediteur() { return expediteur.get(); }
    public String getObjet() { return objet.get(); }
    public String getCotation() { return cotation.get(); }
    public String getDateCotation() { return dateCotation.get(); }
    public String getSousDirection() { return sousDirection.get(); }
}
