package com.sysco.web.service.datamgmt;

/** One row of {@code user_records.xlsx} — same columns as JavaFX {@code DataEntry}. */
public record ExcelUserRecord(
        String dateEnregistrement,
        String expediteur,
        String objet,
        String cotation,
        String dateCotation,
        String sousDirection) {}
