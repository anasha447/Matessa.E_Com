package com.matessa.user.mapper;

import com.matessa.user.dto.response.AddressResponse;
import com.matessa.user.entity.Address;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressResponse toResponse(Address address);
}
