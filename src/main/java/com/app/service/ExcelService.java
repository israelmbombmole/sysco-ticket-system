package com.app.service;

import com.app.model.DataEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Apache POI persistence for the user data Excel workbook. Logic matches {@code sysco-ticket-system-preview}
 * (yearly sheets, same columns) with path resolution, safe cell reading, and directory creation on write.
 */
public class ExcelService {

    /** Set {@code -Dsysco.excel.data=/path/user_records.xlsx} or env {@code SYSCO_EXCEL_DATA} to override. */
    private static File dataFile() {
        String p = System.getProperty("sysco.excel.data");
        if (p == null || p.isBlank()) {
            p = System.getenv("SYSCO_EXCEL_DATA");
        }
        if (p != null && !p.isBlank()) {
            return new File(p.trim());
        }
        return new File(System.getProperty("user.dir", "."), "data" + File.separator + "user_records.xlsx");
    }

    private static final String[] HEADERS = {
        "Date enregistrement",
        "Expediteur",
        "Objet",
        "Cotation",
        "Date cotation",
        "Sous-Direction"
    };

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private static Workbook getWorkbook() throws Exception {
        File file = dataFile();
        if (!file.exists()) {
            return new XSSFWorkbook();
        }
        return new XSSFWorkbook(new FileInputStream(file));
    }

    private static void ensureParentDir() throws java.io.IOException {
        File f = dataFile();
        File parent = f.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
    }

    private static Sheet getOrCreateYearSheet(Workbook wb, int year) {

        String sheetName = "Donnees-" + year;
        Sheet sheet = wb.getSheet(sheetName);

        if (sheet == null) {
            sheet = wb.createSheet(sheetName);

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

    public static void append(
            String dateEnreg,
            String expediteur,
            String objet,
            String cotation,
            String dateCotation,
            String sousDirection
    ) {

        try (Workbook wb = getWorkbook()) {

            int year = yearFromEnregistrement(dateEnreg);

            Sheet sheet = getOrCreateYearSheet(wb, year);

            int rowNum = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowNum);

            row.createCell(0).setCellValue(dateEnreg);
            row.createCell(1).setCellValue(expediteur);
            row.createCell(2).setCellValue(objet);
            row.createCell(3).setCellValue(cotation);
            row.createCell(4).setCellValue(dateCotation);
            row.createCell(5).setCellValue(sousDirection);

            ensureParentDir();
            try (FileOutputStream out = new FileOutputStream(dataFile())) {
                wb.write(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int yearFromEnregistrement(String dateEnreg) {
        if (dateEnreg == null || dateEnreg.isBlank() || "N/A".equalsIgnoreCase(dateEnreg.trim())) {
            return LocalDate.now().getYear();
        }
        try {
            return LocalDate.parse(dateEnreg.trim()).getYear();
        } catch (DateTimeParseException e) {
            return LocalDate.now().getYear();
        }
    }

    public static ObservableList<DataEntry> readAll() {

        ObservableList<DataEntry> list = FXCollections.observableArrayList();

        try (Workbook wb = getWorkbook()) {

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {

                Sheet sheet = wb.getSheetAt(i);

                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {

                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String a = getCellValue(row.getCell(0));
                    String b = getCellValue(row.getCell(1));
                    String c2 = getCellValue(row.getCell(2));
                    if (a.isEmpty() && b.isEmpty() && c2.isEmpty()) {
                        continue;
                    }

                    list.add(new DataEntry(
                            a,
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
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell);
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

                    if (row.getRowNum() == 0) {
                        continue;
                    }

                    String date = getCellValue(row.getCell(0));
                    String exp = getCellValue(row.getCell(1));
                    String obj = getCellValue(row.getCell(2));

                    if (date.equals(original.getDateEnregistrement())
                            && exp.equals(original.getExpediteur())
                            && obj.equals(original.getObjet())) {

                        ensureCell(row, 0).setCellValue(newDateEnreg);
                        ensureCell(row, 1).setCellValue(newExp);
                        ensureCell(row, 2).setCellValue(newObj);
                        ensureCell(row, 3).setCellValue(newCot);
                        ensureCell(row, 4).setCellValue(newDateCot);
                        ensureCell(row, 5).setCellValue(newSousDir);
                    }
                }
            }

            ensureParentDir();
            try (FileOutputStream out = new FileOutputStream(dataFile())) {
                wb.write(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Cell ensureCell(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            c = row.createCell(idx);
        }
        return c;
    }

    public static void delete(DataEntry entry) {

        try (Workbook wb = getWorkbook()) {

            boolean deleted = false;

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {

                Sheet sheet = wb.getSheetAt(i);

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {

                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }

                    String dateEnreg = getCellValue(row.getCell(0));
                    String expediteur = getCellValue(row.getCell(1));
                    String objet = getCellValue(row.getCell(2));
                    String cotation = getCellValue(row.getCell(3));
                    String dateCotation = getCellValue(row.getCell(4));
                    String sousDirection = getCellValue(row.getCell(5));

                    if (dateEnreg.equals(entry.getDateEnregistrement())
                            && expediteur.equals(entry.getExpediteur())
                            && objet.equals(entry.getObjet())
                            && cotation.equals(entry.getCotation())
                            && dateCotation.equals(entry.getDateCotation())
                            && sousDirection.equals(entry.getSousDirection())) {

                        sheet.removeRow(row);

                        if (r < sheet.getLastRowNum()) {
                            sheet.shiftRows(r + 1, sheet.getLastRowNum(), -1);
                        }

                        deleted = true;
                        break;
                    }
                }

                if (deleted) {
                    break;
                }
            }

            ensureParentDir();
            try (FileOutputStream out = new FileOutputStream(dataFile())) {
                wb.write(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
