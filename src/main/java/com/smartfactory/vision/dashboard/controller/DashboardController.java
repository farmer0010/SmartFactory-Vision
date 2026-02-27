package com.smartfactory.vision.dashboard.controller;

import com.smartfactory.vision.auth.entity.AppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("userRole", user.getRole().name());
        }
        return "index";
    }

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("userRole", user.getRole().name());
        }
        return "history";
    }

    @GetMapping("/history/analytics")
    public String analytics(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("userRole", user.getRole().name());
        }
        return "analytics";
    }

    @GetMapping("/history/reports")
    public String reports(@AuthenticationPrincipal AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("userRole", user.getRole().name());
        }
        return "reports";
    }
}
