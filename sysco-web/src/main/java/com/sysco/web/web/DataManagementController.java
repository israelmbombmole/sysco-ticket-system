package com.sysco.web.web;

import com.sysco.web.service.DataManagementService;
import com.sysco.web.service.datamgmt.ExcelUserRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Controller
@RequestMapping("/app/data-management")
@RequiredArgsConstructor
public class DataManagementController {

    private static final String DATA_MANAGEMENT_READ_ACCESS =
            "hasRole('ADMIN') or hasAuthority('DATA_MANAGEMENT') or hasAuthority('DATA_MANAGEMENT_READ') or "
                    + "hasAuthority('DATA_MANAGEMENT_WRITE')";
    private static final String DATA_MANAGEMENT_WRITE_ACCESS =
            "hasRole('ADMIN') or hasAuthority('DATA_MANAGEMENT') or hasAuthority('DATA_MANAGEMENT_WRITE')";

    private final DataManagementService dataManagementService;

    @GetMapping
    @PreAuthorize(DATA_MANAGEMENT_READ_ACCESS)
    public String page(
            @RequestParam(required = false) String registrationDate,
            @RequestParam(required = false) String expediteur,
            @RequestParam(required = false) String cotation,
            @RequestParam(required = false) String selected,
            Model model) {

        var page = dataManagementService.buildPage(registrationDate, expediteur, cotation, selected);

        model.addAttribute("pageTitleKey", "nav.dataManagement");
        model.addAttribute("page", page);
        model.addAttribute("filterQs", filterQueryPrefix(registrationDate, expediteur, cotation));
        return "app/data-management";
    }

    @GetMapping("/export")
    @PreAuthorize(DATA_MANAGEMENT_READ_ACCESS)
    public ResponseEntity<ByteArrayResource> exportCsv() {
        byte[] csv = dataManagementService.exportCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gestion-des-donnees.csv\"")
                .body(new ByteArrayResource(csv));
    }

    @PostMapping("/import")
    @PreAuthorize(DATA_MANAGEMENT_WRITE_ACCESS)
    public String importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String filterRegistrationDate,
            @RequestParam(required = false) String filterExpediteur,
            @RequestParam(required = false) String filterCotation,
            RedirectAttributes redirectAttributes) {
        try {
            dataManagementService.importData(file);
            redirectAttributes.addFlashAttribute("flashSuccess", "dataMgmt.flash.imported");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.importEmpty");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.import");
        }
        return redirectWithFilters(filterRegistrationDate, filterExpediteur, filterCotation);
    }

    private static String filterQueryPrefix(String registrationDate, String expediteur, String cotation) {
        UriComponentsBuilder b = UriComponentsBuilder.newInstance();
        if (registrationDate != null && !registrationDate.isBlank()) {
            b.queryParam("registrationDate", registrationDate);
        }
        if (expediteur != null && !expediteur.isBlank()) {
            b.queryParam("expediteur", expediteur);
        }
        if (cotation != null && !cotation.isBlank()) {
            b.queryParam("cotation", cotation);
        }
        String q = b.build().getQuery();
        return q == null ? "" : q + "&";
    }

    @PostMapping("/delete")
    @PreAuthorize(DATA_MANAGEMENT_WRITE_ACCESS)
    public String delete(
            @RequestParam String rowKey,
            @RequestParam(required = false) String filterRegistrationDate,
            @RequestParam(required = false) String filterExpediteur,
            @RequestParam(required = false) String filterCotation,
            RedirectAttributes redirectAttributes) {

        try {
            dataManagementService.deleteRow(rowKey);
            redirectAttributes.addFlashAttribute("flashSuccess", "dataMgmt.flash.deleted");
        } catch (IllegalArgumentException e) {
            if ("notFound".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.notFound");
            } else {
                redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.badKey");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.generic");
        }

        return redirectWithFilters(filterRegistrationDate, filterExpediteur, filterCotation);
    }

    @PostMapping("/update")
    @PreAuthorize(DATA_MANAGEMENT_WRITE_ACCESS)
    public String update(
            @RequestParam String originalKey,
            @RequestParam String dateEnregistrement,
            @RequestParam String expediteur,
            @RequestParam String objet,
            @RequestParam String cotation,
            @RequestParam String dateCotation,
            @RequestParam String sousDirection,
            @RequestParam(required = false) String filterRegistrationDate,
            @RequestParam(required = false) String filterExpediteur,
            @RequestParam(required = false) String filterCotation,
            RedirectAttributes redirectAttributes) {

        ExcelUserRecord updated =
                new ExcelUserRecord(
                        nz(dateEnregistrement),
                        nz(expediteur),
                        nz(objet),
                        nz(cotation),
                        nz(dateCotation),
                        nz(sousDirection));
        try {
            dataManagementService.updateRow(originalKey, updated);
            redirectAttributes.addFlashAttribute("flashSuccess", "dataMgmt.flash.updated");
        } catch (IllegalArgumentException e) {
            if ("required".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.required");
            } else if ("notFound".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.notFound");
            } else {
                redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.badKey");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flashError", "dataMgmt.error.generic");
        }

        return redirectWithFilters(filterRegistrationDate, filterExpediteur, filterCotation);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String redirectWithFilters(String registrationDate, String expediteur, String cotation) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/data-management");
        if (registrationDate != null && !registrationDate.isBlank()) {
            b.queryParam("registrationDate", registrationDate);
        }
        if (expediteur != null && !expediteur.isBlank()) {
            b.queryParam("expediteur", expediteur);
        }
        if (cotation != null && !cotation.isBlank()) {
            b.queryParam("cotation", cotation);
        }
        return "redirect:" + b.encode().build().toUriString();
    }
}
