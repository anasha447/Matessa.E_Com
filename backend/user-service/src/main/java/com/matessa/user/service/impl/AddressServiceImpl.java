package com.matessa.user.service.impl;

import com.matessa.user.dto.request.AddressRequest;
import com.matessa.user.dto.response.AddressResponse;
import com.matessa.user.entity.Address;
import com.matessa.user.exception.ResourceNotFoundException;
import com.matessa.user.mapper.AddressMapper;
import com.matessa.user.repository.AddressRepository;
import com.matessa.user.service.AddressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressRepository addressRepository, AddressMapper addressMapper) {
        this.addressRepository = addressRepository;
        this.addressMapper = addressMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AddressResponse> listForUser(Long userId, Pageable pageable) {
        return addressRepository.findByUserId(userId, pageable).map(addressMapper::toResponse);
    }

    @Override
    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        Address address = new Address();
        address.setUserId(userId);
        apply(address, request);
        setDefault(userId, address, request.defaultAddress());
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address address = ownedAddress(userId, addressId);
        apply(address, request);
        setDefault(userId, address, request.defaultAddress());
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        addressRepository.delete(ownedAddress(userId, addressId));
    }

    /** Ownership check: returns the address only if it belongs to the caller, else 404. */
    private Address ownedAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
    }

    private void apply(Address address, AddressRequest request) {
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
        address.setType(request.type());
    }

    /** A user has at most one default address; setting a new default clears the previous one. */
    private void setDefault(Long userId, Address address, boolean makeDefault) {
        if (makeDefault) {
            addressRepository.findByUserIdAndDefaultAddressTrue(userId)
                    .forEach(existing -> existing.setDefaultAddress(false));
        }
        address.setDefaultAddress(makeDefault);
    }
}
