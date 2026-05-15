package com.sysco.web.service.datamgmt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache POI persistence for the user data workbook — mirrors {@code com.app.service.ExcelService}
 * (yearly sheets, same columns, path resolution).
 */
@Component
public class UserRecordsExcelStore {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final Object fileLock = new Object();

    @Value("${sysco.excel.data:}")
    private String configuredExcelPath;

    public Path dataFilePath() {
        String sys = System.getProperty("sysco.excel.data");
        if (sys != null && !sys.isBlank()) {
            return Path.of(sys.trim()).toAbsolutePath().normalize();
        }
        if (configuredExcelPath != null && !configuredExcelPath.isBlank()) {
            return Path.of(configuredExcelPath.trim()).toAbsolutePath().normalize();
        }
        String env = System.getenv("SYSCO_EXCEL_DATA");
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", "."), "data", "user_records.xlsx")
                .toAbsolutePath()
                .normalize();
    }

    private static void ensureParentDir(Path file) throws java.io.IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private Workbook openWorkbook() throws Exception {
        Path f = dataFilePath();
        if (!Files.exists(f)) {
            return new XSSFWorkbook();
        }
        try (FileInputStream in = new FileInputStream(f.toFile())) {
            return new XSSFWorkbook(in);
        }
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell);
    }

    private static Cell ensureCell(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            c = row.createCell(idx);
        }
        return c;
    }

    public List<ExcelUserRecord> readAll() {
        synchronized (fileLock) {
            List<ExcelUserRecord> list = new ArrayList<>();
            try (Workbook wb = openWorkbook()) {
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
                        list.add(
                                new ExcelUserRecord(
                                        a,
                                        getCellValue(row.getCell(1)),
                                        getCellValue(row.getCell(2)),
                                        getCellValue(row.getCell(3)),
                                        getCellValue(row.getCell(4)),
                                        getCellValue(row.getCell(5))));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not read Excel user records", e);
            }
            return list;
        }
    }

    public void update(ExcelUserRecord original, ExcelUserRecord updated) {
        synchronized (fileLock) {
            Path file = dataFilePath();
            boolean updatedRow = false;
            try (Workbook wb = openWorkbook()) {
                for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                    Sheet sheet = wb.getSheetAt(si);
                    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) {
                            continue;
                        }
                        String date = getCellValue(row.getCell(0));
                        String exp = getCellValue(row.getCell(1));
                        String obj = getCellValue(row.getCell(2));
                        if (date.equals(original.dateEnregistrement())
                                && exp.equals(original.expediteur())
                                && obj.equals(original.objet())) {
                            ensureCell(row, 0).setCellValue(nullToEmpty(updated.dateEnregistrement()));
                            ensureCell(row, 1).setCellValue(nullToEmpty(updated.expediteur()));
                            ensureCell(row, 2).setCellValue(nullToEmpty(updated.objet()));
                            ensureCell(row, 3).setCellValue(nullToEmpty(updated.cotation()));
                            ensureCell(row, 4).setCellValue(nullToEmpty(updated.dateCotation()));
                            ensureCell(row, 5).setCellValue(nullToEmpty(updated.sousDirection()));
                            updatedRow = true;
                            break;
                        }
                    }
                    if (updatedRow) {
                        break;
                    }
                }
                if (!updatedRow) {
                    throw new IllegalArgumentException("notFound");
                }
                ensureParentDir(file);
                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    wb.write(fos);
                }
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                throw new IllegalStateException("Could not update Excel row", e);
            }
        }
    }

    public void delete(ExcelUserRecord entry) {
        synchronized (fileLock) {
            Path file = dataFilePath();
            try (Workbook wb = openWorkbook()) {
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
                        if (dateEnreg.equals(entry.dateEnregistrement())
                                && expediteur.equals(entry.expediteur())
                                && objet.equals(entry.objet())
                                && cotation.equals(entry.cotation())
                                && dateCotation.equals(entry.dateCotation())
                                && sousDirection.equals(entry.sousDirection())) {
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
                ensureParentDir(file);
                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    wb.write(fos);
                }
                if (!deleted) {
                    throw new IllegalArgumentException("notFound");
                }
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                throw new IllegalStateException("Could not delete Excel row", e);
            }
        }
    }

    public void append(ExcelUserRecord record) {
        synchronized (fileLock) {
            Path file = dataFilePath();
            try (Workbook wb = openWorkbook()) {
                Sheet sheet = ensureWritableSheet(wb);
                int nextRow = Math.max(1, sheet.getLastRowNum() + 1);
                Row row = sheet.createRow(nextRow);
                ensureCell(row, 0).setCellValue(nullToEmpty(record.dateEnregistrement()));
                ensureCell(row, 1).setCellValue(nullToEmpty(record.expediteur()));
                ensureCell(row, 2).setCellValue(nullToEmpty(record.objet()));
                ensureCell(row, 3).setCellValue(nullToEmpty(record.cotation()));
                ensureCell(row, 4).setCellValue(nullToEmpty(record.dateCotation()));
                ensureCell(row, 5).setCellValue(nullToEmpty(record.sousDirection()));
                ensureParentDir(file);
                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    wb.write(fos);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not append Excel row", e);
            }
        }
    }

    public void appendAll(List<ExcelUserRecord> records) {
        synchronized (fileLock) {
            Path file = dataFilePath();
            try (Workbook wb = openWorkbook()) {
                Sheet sheet = ensureWritableSheet(wb);
                int nextRow = Math.max(1, sheet.getLastRowNum() + 1);
                for (ExcelUserRecord record : records) {
                    if (record == null) {
                        continue;
                    }
                    Row row = sheet.createRow(nextRow++);
                    ensureCell(row, 0).setCellValue(nullToEmpty(record.dateEnregistrement()));
                    ensureCell(row, 1).setCellValue(nullToEmpty(record.expediteur()));
                    ensureCell(row, 2).setCellValue(nullToEmpty(record.objet()));
                    ensureCell(row, 3).setCellValue(nullToEmpty(record.cotation()));
                    ensureCell(row, 4).setCellValue(nullToEmpty(record.dateCotation()));
                    ensureCell(row, 5).setCellValue(nullToEmpty(record.sousDirection()));
                }
                ensureParentDir(file);
                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    wb.write(fos);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not append Excel rows", e);
            }
        }
    }

    private static Sheet ensureWritableSheet(Workbook wb) {
        if (wb.getNumberOfSheets() == 0) {
            Sheet created = wb.createSheet(String.valueOf(Year.now().getValue()));
            Row header = created.createRow(0);
            ensureCell(header, 0).setCellValue("Date d’enregistrement");
            ensureCell(header, 1).setCellValue("Expéditeur");
            ensureCell(header, 2).setCellValue("Objet");
            ensureCell(header, 3).setCellValue("Cotation");
            ensureCell(header, 4).setCellValue("Date cotation");
            ensureCell(header, 5).setCellValue("Sous-direction");
            return created;
        }
        return wb.getSheetAt(0);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
