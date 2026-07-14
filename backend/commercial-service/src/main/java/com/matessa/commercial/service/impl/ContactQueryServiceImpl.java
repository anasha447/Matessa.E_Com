package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.ContactQueryRequest;
import com.matessa.commercial.dto.response.ContactQueryResponse;
import com.matessa.commercial.entity.ContactQuery;
import com.matessa.commercial.entity.ContactQueryStatus;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.ContactQueryMapper;
import com.matessa.commercial.repository.ContactQueryRepository;
import com.matessa.commercial.service.ContactQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ContactQueryServiceImpl implements ContactQueryService {

    private final ContactQueryRepository repository;
    private final ContactQueryMapper mapper;

    public ContactQueryServiceImpl(ContactQueryRepository repository, ContactQueryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ContactQueryResponse submit(ContactQueryRequest request) {
        ContactQuery query = new ContactQuery();
        query.setEmail(request.email().trim());
        query.setQueryType(request.queryType());
        query.setMessage(request.message());
        query.setStatus(ContactQueryStatus.OPEN);
        query.setCreatedAt(Instant.now());
        return mapper.toResponse(repository.save(query));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactQueryResponse> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public ContactQueryResponse updateStatus(Long id, ContactQueryStatus status) {
        ContactQuery query = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact query not found: " + id));
        query.setStatus(status);
        return mapper.toResponse(repository.save(query));
    }
}
