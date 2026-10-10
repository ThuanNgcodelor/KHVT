package com.example.quanlymuahang.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the bundled React entry point only for known UI routes. */
@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaController {
    @GetMapping({"/", "/login", "/change-password", "/modules", "/dashboard",
            "/purchase-orders", "/purchase-orders/new", "/purchase-orders/{id:[0-9]+}",
            "/purchase-orders/{id:[0-9]+}/edit", "/catalog/materials", "/catalog/suppliers",
            "/price-search", "/imports", "/admin/employees", "/admin/departments",
            "/admin/positions", "/admin/users"})
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(new ClassPathResource("static/index.html"));
    }
}
