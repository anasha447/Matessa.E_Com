package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.ContactQueryRequest;
import com.matessa.commercial.dto.request.ContactQueryStatusRequest;
import com.matessa.commercial.dto.response.ContactQueryResponse;
import com.matessa.commercial.service.ContactQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront contact form. {@code POST} is public (guests may submit); listing and status
 * changes are staff-only (MANAGER/ADMIN), enforced by SecurityConfig's catch-all.
 */
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactQueryService contactQueryService;

    public ContactController(ContactQueryService contactQueryService) {
        this.contactQueryService = contactQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactQueryResponse submit(@Valid @RequestBody ContactQueryRequest request) {
        return contactQueryService.submit(request);
    }

    @GetMapping
    public Page<ContactQueryResponse> list(Pageable pageable) {
        return contactQueryService.list(pageable);
    }

    @PatchMapping("/{id}/status")
    public ContactQueryResponse updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody ContactQueryStatusRequest request) {
        return contactQueryService.updateStatus(id, request.status());
    }
}
