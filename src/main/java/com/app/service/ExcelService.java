package com.app.service;


import com.app.model.DataEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDate;

public class ExcelService {

    private static final String FILE_PATH =
        "C:/sqlite/javafx-audit-system/data/user_records.xlsx";
    

    private static final String[] HEADERS = {
        "Date enregistrement",
        "Expediteur",
        "Objet",
        "Cotation",
        "Date cotation",
        "Sous-Direction"
    };

    // =========================
    // GET OR CREATE WORKBOOK
    // =========================
    private static Workbook getWorkbook() throws Exception {
        File file = new File(FILE_PATH);

        if (!file.exists()) {
            return new XSSFWorkbook();
        }

        return new XSSFWorkbook(new FileInputStream(file));
    }

    // =========================
    // GET OR CREATE YEAR SHEET
    // =========================
    private static Sheet getOrCreateYearSheet(Workbook wb, int year) {

        String sheetName = "Donnees-" + year;
        Sheet sheet = wb.getSheet(sheetName);

        if (sheet == null) {
            sheet = wb.createSheet(sheetName);

            // 🔹 Header row
            Row header = sheet.createRow(0);

            CellStyle style = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            style.setFont(font);

            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(style);
                sheet.autoSizeColumn(i);
            }
        }

        return sheet;
    }

    // =========================
    // APPEND DATA
    // =========================
    public static void append(
            String dateEnreg,
            String expediteur,
            String objet,
            String cotation,
            String dateCotation,
            String sousDirection
    ) {

        try (Workbook wb = getWorkbook()) {

            // 🔹 Determine year from dateEnreg
            int year = LocalDate.parse(dateEnreg).getYear();

            Sheet sheet = getOrCreateYearSheet(wb, year);

            int rowNum = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowNum);

            row.createCell(0).setCellValue(dateEnreg);
            row.createCell(1).setCellValue(expediteur);
            row.createCell(2).setCellValue(objet);
            row.createCell(3).setCellValue(cotation);
            row.createCell(4).setCellValue(dateCotation);
            row.createCell(5).setCellValue(sousDirection);

            try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
                wb.write(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static ObservableList<DataEntry> readAll() {

    ObservableList<DataEntry> list = FXCollections.observableArrayList();

    try (Workbook wb = getWorkbook()) {

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet sheet = wb.getSheetAt(i);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                list.add(new DataEntry(
                        getCellValue(row.getCell(0)),
                        getCellValue(row.getCell(1)),
                        getCellValue(row.getCell(2)),
                        getCellValue(row.getCell(3)),
                        getCellValue(row.getCell(4)),
                        getCellValue(row.getCell(5))
                ));
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

private static String getCellValue(Cell cell) {
    if (cell == null) return "";
    cell.setCellType(CellType.STRING);
    return cell.getStringCellValue();
}
    
 public static void update(
        DataEntry original,
        String newDateEnreg,
        String newExp,
        String newObj,
        String newCot,
        String newDateCot,
        String newSousDir
) {
    try (Workbook wb = getWorkbook()) {

        for (Sheet sheet : wb) {

            for (Row row : sheet) {

                if (row.getRowNum() == 0) continue;

                String date = row.getCell(0).getStringCellValue();
                String exp = row.getCell(1).getStringCellValue();
                String obj = row.getCell(2).getStringCellValue();

                if (date.equals(original.getDateEnregistrement())
                        && exp.equals(original.getExpediteur())
                        && obj.equals(original.getObjet())) {

                    row.getCell(0).setCellValue(newDateEnreg);
                    row.getCell(1).setCellValue(newExp);
                    row.getCell(2).setCellValue(newObj);
                    row.getCell(3).setCellValue(newCot);
                    row.getCell(4).setCellValue(newDateCot);
                    row.getCell(5).setCellValue(newSousDir);
                }
            }
        }

        try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
            wb.write(out);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

 
 // =========================
// DELETE FROM EXCEL
// =========================
public static void delete(DataEntry entry) {

    try (Workbook wb = getWorkbook()) {

        boolean deleted = false;

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet sheet = wb.getSheetAt(i);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {

                Row row = sheet.getRow(r);
                if (row == null) continue;

                String dateEnreg = row.getCell(0).getStringCellValue();
                String expediteur = row.getCell(1).getStringCellValue();
                String objet = row.getCell(2).getStringCellValue();
                String cotation = row.getCell(3).getStringCellValue();
                String dateCotation = row.getCell(4).getStringCellValue();
                String sousDirection = row.getCell(5).getStringCellValue();

                if (dateEnreg.equals(entry.getDateEnregistrement()) &&
                    expediteur.equals(entry.getExpediteur()) &&
                    objet.equals(entry.getObjet()) &&
                    cotation.equals(entry.getCotation()) &&
                    dateCotation.equals(entry.getDateCotation()) &&
                    sousDirection.equals(entry.getSousDirection())) {

                    sheet.removeRow(row);

                    if (r < sheet.getLastRowNum()) {
                        sheet.shiftRows(r + 1, sheet.getLastRowNum(), -1);
                    }

                    deleted = true;
                    break;
                }
            }

            if (deleted) break;
        }

        try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
            wb.write(out);
        }

        System.out.println("Deleted from Excel successfully");

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    
    
    
}
