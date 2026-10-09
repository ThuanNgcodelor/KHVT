package com.example.quanlymuahang.identity.domain.model;

public enum PermissionCode {
    ALL("*"),
    USER_MANAGE("USER_MANAGE"),
    PERSONNEL_MANAGE("PERSONNEL_MANAGE"),
    CATALOG_EDIT("CATALOG_EDIT"),
    IMPORT_RUN("IMPORT_RUN"),
    PO_CREATE("PO_CREATE"),
    PO_EDIT("PO_EDIT"),
    PO_READ("PO_READ"),
    PRICE_READ("PRICE_READ"),
    AUDIT_READ("AUDIT_READ");

    private final String value;

    PermissionCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
