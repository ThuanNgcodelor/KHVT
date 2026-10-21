package com.example.quanlymuahang.sharedkernel.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SafeSortUtil {
    private SafeSortUtil() {}

    public static Pageable sanitize(Pageable pageable, Map<String, String> allowedMappings, Sort defaultSort) {
        if (pageable == null) {
            return PageRequest.of(0, 25, defaultSort);
        }
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        List<Sort.Order> safeOrders = new ArrayList<>();
        boolean hasPoNumber = false;
        boolean hasId = false;
        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            if (allowedMappings.containsKey(property)) {
                String targetProperty = allowedMappings.get(property);
                safeOrders.add(new Sort.Order(order.getDirection(), targetProperty));
                if ("poNumber".equals(targetProperty)) hasPoNumber = true;
                if ("id".equals(targetProperty)) hasId = true;
            }
        }

        if (safeOrders.isEmpty()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        // Secondary tie-breakers for deterministic PO ordering
        if (!hasPoNumber && allowedMappings.containsValue("poNumber")) {
            safeOrders.add(new Sort.Order(Sort.Direction.ASC, "poNumber"));
        }
        if (!hasId && allowedMappings.containsValue("id")) {
            safeOrders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(safeOrders));
    }
}
