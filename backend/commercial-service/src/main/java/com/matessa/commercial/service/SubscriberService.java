package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.SubscribeRequest;
import com.matessa.commercial.dto.response.SubscribeResponse;
import com.matessa.commercial.dto.response.SubscriberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriberService {

    /** Public: subscribe an email and return a welcome coupon. Idempotent — a repeat email
     *  gets its existing coupon back, never a duplicate. */
    SubscribeResponse join(SubscribeRequest request);

    /** Staff: newest-first page of subscribers. */
    Page<SubscriberResponse> list(Pageable pageable);
}
