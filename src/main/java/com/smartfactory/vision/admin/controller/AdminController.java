package com.smartfactory.vision.admin.controller;

import com.smartfactory.vision.auth.entity.AppUser;
import com.smartfactory.vision.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    private void addCommonAttributes(AppUser user, Model model) {
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("userRole", user.getRole().name());
        }
    }

    @GetMapping({ "", "/" })
    public String adminHome(@AuthenticationPrincipal AppUser user, Model model) {
        addCommonAttributes(user, model);
        model.addAttribute("userCount", userRepository.count());
        return "admin/index";
    }

    @GetMapping("/users")
    public String adminUsers(@AuthenticationPrincipal AppUser user, Model model) {
        addCommonAttributes(user, model);
        return "admin/users";
    }

    @GetMapping("/cameras")
    public String adminCameras(@AuthenticationPrincipal AppUser user, Model model) {
        addCommonAttributes(user, model);
        return "admin/cameras";
    }

    @GetMapping("/settings")
    public String adminSettings(@AuthenticationPrincipal AppUser user, Model model) {
        addCommonAttributes(user, model);
        return "admin/settings";
    }

    @GetMapping("/audit")
    public String adminAudit(@AuthenticationPrincipal AppUser user, Model model) {
        addCommonAttributes(user, model);
        return "admin/audit";
    }
}
