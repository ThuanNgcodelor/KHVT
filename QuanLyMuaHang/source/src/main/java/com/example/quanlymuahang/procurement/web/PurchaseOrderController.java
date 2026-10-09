package com.example.quanlymuahang.procurement.web;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderStatus;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.example.quanlymuahang.procurement.application.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService service;
    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_READ')")
    public Page<PurchaseOrderService.OrderView> search(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) PurchaseOrderStatus status,
                                                        @PageableDefault(size = 25) Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_READ')")
    public PurchaseOrderService.OrderView get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_CREATE')")
    public PurchaseOrderService.OrderView create(@Valid @RequestBody OrderRequest body, Authentication authentication) {
        return service.create(body.command(), actor(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_EDIT')")
    public PurchaseOrderService.OrderView update(@PathVariable long id, @Valid @RequestBody OrderRequest body, Authentication authentication) {
        return service.update(id, body.command(), actor(authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_CANCEL')")
    public PurchaseOrderService.OrderView cancel(@PathVariable long id, @RequestBody(required = false) CancelRequest body, Authentication authentication) {
        return service.cancel(id, body == null ? null : body.reason(), actor(authentication));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_READ')")
    public ResponseEntity<byte[]> pdf(@PathVariable long id, Authentication authentication) {
        PurchaseOrderService.PdfFile file = service.pdf(id, actor(authentication));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    private static long actor(Authentication authentication) { return ((AccountPrincipal) authentication.getPrincipal()).id(); }
    public record CancelRequest(@Size(max = 500) String reason) {}
    public record OrderRequest(@NotNull Long supplierId, LocalDate orderDate, @NotNull CurrencyCode currency,
                               @NotNull @PositiveOrZero BigDecimal vatPercent, @Size(max = 1000) String note,
                               @Size(max = 255) String preparedBy, @Size(max = 500) String changeReason,
                               @NotEmpty @Size(max = 200) List<@Valid ItemRequest> items) {
        PurchaseOrderService.OrderCommand command() {
            return new PurchaseOrderService.OrderCommand(supplierId, orderDate, currency, vatPercent, note, preparedBy, changeReason,
                    items.stream().map(ItemRequest::command).toList());
        }
    }
    public record ItemRequest(Long materialId, String materialCode, @NotBlank @Size(max = 500) String materialName,
                              @Size(max = 1000) String specification, @Size(max = 100) String unit,
                              @Positive BigDecimal quantity, @Size(max = 255) String quantityText,
                              @NotNull @Positive BigDecimal unitPrice) {
        PurchaseOrderService.ItemCommand command() {
            return new PurchaseOrderService.ItemCommand(materialId, materialCode, materialName, specification, unit, quantity, quantityText, unitPrice);
        }
    }
}
