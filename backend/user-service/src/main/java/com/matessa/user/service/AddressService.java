package com.matessa.user.service;

import com.matessa.user.dto.request.AddressRequest;
import com.matessa.user.dto.response.AddressResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AddressService {

    Page<AddressResponse> listForUser(Long userId, Pageable pageable);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    void delete(Long userId, Long addressId);
}
