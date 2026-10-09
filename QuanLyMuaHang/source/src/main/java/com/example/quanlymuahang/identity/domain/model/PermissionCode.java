package com.example.quanlymuahang.identity.domain.model;

public enum PermissionCode {
    ALL("*"),
    USER_READ("USER_READ"),
    USER_MANAGE("USER_MANAGE"),
    ROLE_MANAGE("ROLE_MANAGE"),
    PERSONNEL_READ("PERSONNEL_READ"),
    PERSONNEL_MANAGE("PERSONNEL_MANAGE"),
    CATALOG_READ("CATALOG_READ"),
    CATALOG_MANAGE("CATALOG_MANAGE"),
    PRICE_READ("PRICE_READ"),
    PO_READ("PO_READ"),
    PO_CREATE("PO_CREATE"),
    PO_EDIT("PO_EDIT"),
    PO_CANCEL("PO_CANCEL"),
    IMPORT_LEGACY("IMPORT_LEGACY"),
    IMPORT_OPERATIONAL("IMPORT_OPERATIONAL"),
    AUDIT_READ("AUDIT_READ");

    private final String value;

    PermissionCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
