package com.booking.system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/login",
            "/register",
            "/forgot-password",
            "/rooms",
            "/rooms/**",
            "/cars",
            "/cars/**",
            "/notifications",
            "/profile",
            "/admin",
            "/admin/**",
            "/manager",
            "/manager/**"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
