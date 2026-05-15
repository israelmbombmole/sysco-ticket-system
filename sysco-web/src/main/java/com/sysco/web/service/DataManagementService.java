package com.sysco.web.service;

import com.sysco.web.service.datamgmt.ExcelUserRecord;
import com.sysco.web.service.datamgmt.UserRecordsExcelStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DataManagementService {

    private static final Logger log = LoggerFactory.getLogger(DataManagementService.class);
    private static final String SEP = "\u001e";

    private final UserRecordsExcelStore excelStore;

    public DataManagementPage buildPage(
            String registrationDate,
            String expediteur,
            String cotation,
            String selectedKey) {

        String reg = trimToEmpty(registrationDate);
        String exp = trimToEmpty(expediteur);
        String cot = trimToEmpty(cotation);

        List<ExcelUserRecord> all = excelStore.readAll();
        List<ExcelUserRecord> filtered = applyFilters(all, reg, exp, cot);

        List<DataMgmtRow> rows = new ArrayList<>();
        for (ExcelUserRecord r : filtered) {
            rows.add(new DataMgmtRow(r, encodeKey(r)));
        }

        ExcelUserRecord selected = null;
        String selKey = null;
        Optional<ExcelUserRecord> decoded = decodeKey(selectedKey);
        if (decoded.isPresent()) {
            ExcelUserRecord k = decoded.get();
            if (filtered.stream().anyMatch(k::equals)) {
                selected = k;
                selKey = encodeKey(k);
            }
        }

        return new DataManagementPage(
                rows,
                selected,
                selKey,
                reg,
                exp,
                cot,
                excelStore.dataFilePath().toString());
    }

    public void deleteRow(String rowKey) {
        ExcelUserRecord row =
                decodeKey(rowKey).orElseThrow(() -> new IllegalArgumentException("badKey"));
        excelStore.delete(row);
        log.info("Data management: deleted Excel row expediteur={}, objet={}", row.expediteur(), row.objet());
    }

    public void updateRow(String originalKey, ExcelUserRecord updated) {
        ExcelUserRecord original =
                decodeKey(originalKey).orElseThrow(() -> new IllegalArgumentException("badKey"));
        if (updated.dateEnregistrement().isBlank() || updated.expediteur().isBlank() || updated.objet().isBlank()) {
            throw new IllegalArgumentException("required");
        }
        excelStore.update(original, updated);
        log.info(
                "Data management: updated Excel row expediteur={}, objet={}",
                updated.expediteur(),
                updated.objet());
    }

    public void appendCourierResolvedRow(
            String dateEnregistrement,
            String expediteur,
            String objet,
            String sousDirection,
            String cotation,
            String dateCotation) {
        ExcelUserRecord row = new ExcelUserRecord(
                trimToEmpty(dateEnregistrement),
                trimToEmpty(expediteur),
                trimToEmpty(objet),
                trimToEmpty(cotation),
                trimToEmpty(dateCotation),
                trimToEmpty(sousDirection));
        if (row.dateEnregistrement().isBlank() || row.expediteur().isBlank() || row.objet().isBlank()) {
            return;
        }
        excelStore.append(row);
    }

    public byte[] exportCsv() {
        List<ExcelUserRecord> rows = excelStore.readAll();
        StringBuilder sb = new StringBuilder();
        sb.append("Date d’enregistrement,Expéditeur,Objet,Sous-direction,Cotation,Date cotation\n");
        for (ExcelUserRecord r : rows) {
            sb.append(csv(r.dateEnregistrement())).append(',')
                    .append(csv(r.expediteur())).append(',')
                    .append(csv(r.objet())).append(',')
                    .append(csv(r.sousDirection())).append(',')
                    .append(csv(r.cotation())).append(',')
                    .append(csv(r.dateCotation())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public int importData(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("emptyImport");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<ExcelUserRecord> toAppend = name.endsWith(".xlsx") ? parseXlsx(file) : parseCsv(file);
        if (toAppend.isEmpty()) {
            throw new IllegalArgumentException("emptyImport");
        }
        excelStore.appendAll(toAppend);
        return toAppend.size();
    }

    private static List<ExcelUserRecord> parseCsv(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R");
            List<ExcelUserRecord> rows = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isBlank()) {
                    continue;
                }
                if (i == 0 && line.toLowerCase(Locale.ROOT).contains("expéditeur")) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 6) {
                    continue;
                }
                ExcelUserRecord r = new ExcelUserRecord(
                        cols.get(0).trim(),
                        cols.get(1).trim(),
                        cols.get(2).trim(),
                        cols.get(4).trim(),
                        cols.get(5).trim(),
                        cols.get(3).trim());
                if (!r.dateEnregistrement().isBlank() || !r.expediteur().isBlank() || !r.objet().isBlank()) {
                    rows.add(r);
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("importError", e);
        }
    }

    private static List<ExcelUserRecord> parseXlsx(MultipartFile file) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            DataFormatter fmt = new DataFormatter();
            List<ExcelUserRecord> rows = new ArrayList<>();
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                var sh = wb.getSheetAt(si);
                for (int ri = 1; ri <= sh.getLastRowNum(); ri++) {
                    var row = sh.getRow(ri);
                    if (row == null) {
                        continue;
                    }
                    String dateEnreg = fmt.formatCellValue(row.getCell(0)).trim();
                    String expediteur = fmt.formatCellValue(row.getCell(1)).trim();
                    String objet = fmt.formatCellValue(row.getCell(2)).trim();
                    String cotation = fmt.formatCellValue(row.getCell(3)).trim();
                    String dateCotation = fmt.formatCellValue(row.getCell(4)).trim();
                    String sousDirection = fmt.formatCellValue(row.getCell(5)).trim();
                    if (dateEnreg.isBlank() && expediteur.isBlank() && objet.isBlank()) {
                        continue;
                    }
                    rows.add(new ExcelUserRecord(dateEnreg, expediteur, objet, cotation, dateCotation, sousDirection));
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("importError", e);
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static List<ExcelUserRecord> applyFilters(
            List<ExcelUserRecord> all, String registrationDate, String expediteur, String cotation) {

        String cotLower = cotation.toLowerCase(Locale.ROOT);
        String expLower = expediteur.toLowerCase(Locale.ROOT);

        List<ExcelUserRecord> out = new ArrayList<>();
        for (ExcelUserRecord entry : all) {
            boolean match = true;
            if (!cotation.isBlank()) {
                match &= entry.cotation().toLowerCase(Locale.ROOT).contains(cotLower);
            }
            if (!expediteur.isBlank()) {
                match &= entry.expediteur().toLowerCase(Locale.ROOT).contains(expLower);
            }
            if (!registrationDate.isBlank()) {
                match &= entry.dateEnregistrement().equals(registrationDate);
            }
            if (match) {
                out.add(entry);
            }
        }
        return out;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    public static String encodeKey(ExcelUserRecord r) {
        String raw =
                String.join(
                        SEP,
                        r.dateEnregistrement(),
                        r.expediteur(),
                        r.objet(),
                        r.cotation(),
                        r.dateCotation(),
                        r.sousDirection());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<ExcelUserRecord> decodeKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] b = Base64.getUrlDecoder().decode(key);
            String raw = new String(b, StandardCharsets.UTF_8);
            String[] p = raw.split(SEP, -1);
            if (p.length != 6) {
                return Optional.empty();
            }
            return Optional.of(
                    new ExcelUserRecord(p[0], p[1], p[2], p[3], p[4], p[5]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record DataMgmtRow(ExcelUserRecord record, String rowKey) {}

    public record DataManagementPage(
            List<DataMgmtRow> rows,
            ExcelUserRecord selected,
            String selectedRowKey,
            String filterRegistrationDate,
            String filterExpediteur,
            String filterCotation,
            String excelPathDisplay) {}
}
