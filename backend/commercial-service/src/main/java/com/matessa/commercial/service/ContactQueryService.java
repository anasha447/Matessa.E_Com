package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.ContactQueryRequest;
import com.matessa.commercial.dto.response.ContactQueryResponse;
import com.matessa.commercial.entity.ContactQueryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactQueryService {

    /** Public: persist a storefront contact-form submission. */
    ContactQueryResponse submit(ContactQueryRequest request);

    /** Staff: newest-first page of submissions. */
    Page<ContactQueryResponse> list(Pageable pageable);

    /** Staff: mark a query OPEN/RESOLVED. */
    ContactQueryResponse updateStatus(Long id, ContactQueryStatus status);
}
