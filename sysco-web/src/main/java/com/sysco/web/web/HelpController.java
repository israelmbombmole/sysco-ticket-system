package com.sysco.web.web;

import com.sysco.web.service.GuidedTourService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/app/help")
@RequiredArgsConstructor
public class HelpController {

    private final GuidedTourService guidedTourService;

    @PostMapping("/tutorial-completed")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Void> tutorialCompleted(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            guidedTourService.markTutorialCompleted(authentication.getName());
        }
        return ResponseEntity.ok().build();
    }
}
