package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.SubscribeRequest;
import com.matessa.commercial.dto.response.SubscribeResponse;
import com.matessa.commercial.dto.response.SubscriberResponse;
import com.matessa.commercial.service.SubscriberService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Newsletter subscribers. {@code POST} is public (guests sign up for a welcome coupon);
 * listing is staff-only (MANAGER/ADMIN), enforced by SecurityConfig's catch-all.
 */
@RestController
@RequestMapping("/api/subscribers")
public class SubscriberController {

    private final SubscriberService subscriberService;

    public SubscriberController(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscribeResponse join(@Valid @RequestBody SubscribeRequest request) {
        return subscriberService.join(request);
    }

    @GetMapping
    public Page<SubscriberResponse> list(Pageable pageable) {
        return subscriberService.list(pageable);
    }
}
