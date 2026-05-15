package com.sysco.web.web;

import com.sysco.web.service.ChatService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import java.nio.file.Files;

@Controller
@RequestMapping("/app/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String page(
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "conversationId", required = false) Long conversationId,
            @RequestParam(name = "allUsers", defaultValue = "false") boolean allUsers,
            org.springframework.security.core.Authentication auth,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.chat");
        model.addAttribute(
                "page",
                chatService.page(auth.getName(), sousDirectionId, directionId, userId, conversationId, allUsers));
        return "app/chat";
    }

    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    public String send(
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam("toUserId") Long toUserId,
            @RequestParam("text") String text,
            @RequestParam(name = "attachment", required = false) MultipartFile attachment,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            chatService.sendMessage(auth.getName(), directionId, toUserId, text, attachment);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorKey", "chat.error.send");
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/chat").queryParam("userId", toUserId);
        if (directionId != null) {
            b.queryParam("directionId", directionId);
        }
        return "redirect:" + b.encode().build().toUriString();
    }

    @PostMapping("/send-ajax")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<?> sendAjax(
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam("toUserId") Long toUserId,
            @RequestParam(name = "text", required = false) String text,
            @RequestParam(name = "attachment", required = false) MultipartFile attachment,
            org.springframework.security.core.Authentication auth) {
        try {
            return ResponseEntity.ok(chatService.sendMessage(auth.getName(), directionId, toUserId, text, attachment));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", "invalid"));
        }
    }

    @PostMapping("/edit")
    @PreAuthorize("isAuthenticated()")
    public String edit(
            @RequestParam("messageId") Long messageId,
            @RequestParam("newText") String newText,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam("userId") Long userId,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            chatService.editMessage(auth.getName(), messageId, newText);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorKey", "chat.error.send");
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/chat").queryParam("userId", userId);
        if (directionId != null) {
            b.queryParam("directionId", directionId);
        }
        return "redirect:" + b.encode().build().toUriString();
    }

    @GetMapping("/resolve-target")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<?> resolveTarget(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "matricule", required = false) String matricule,
            org.springframework.security.core.Authentication auth) {
        try {
            String effectiveQuery = (query != null && !query.isBlank()) ? query : matricule;
            return ResponseEntity.ok(chatService.resolveTargetByAssistant(auth.getName(), effectiveQuery));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", "notFound"));
        }
    }

    /** Debounced live search for the chat assistant; always 200 with empty candidates when nothing matches. */
    @GetMapping("/search-suggestions")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ChatService.TargetSuggestion searchSuggestions(
            @RequestParam(name = "q", required = false) String q,
            org.springframework.security.core.Authentication auth) {
        return chatService.liveSearchChatTargets(auth.getName(), q);
    }

    @GetMapping("/attachment/{messageId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> attachment(
            @PathVariable("messageId") Long messageId,
            @RequestParam(name = "download", defaultValue = "false") boolean download,
            org.springframework.security.core.Authentication auth) throws Exception {
        var f = chatService.attachment(auth.getName(), messageId);
        byte[] bytes = Files.readAllBytes(f.path());
        String detected = Files.probeContentType(f.path());
        MediaType mediaType = (detected == null || detected.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(detected);
        String cd = (download ? "attachment" : "inline") + "; filename=\"" + f.fileName() + "\"";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .body(new ByteArrayResource(bytes));
    }
}
