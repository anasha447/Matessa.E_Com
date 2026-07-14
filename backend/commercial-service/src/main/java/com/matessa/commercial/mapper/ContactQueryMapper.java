package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.ContactQueryResponse;
import com.matessa.commercial.entity.ContactQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ContactQueryMapper {

    ContactQueryResponse toResponse(ContactQuery query);
}
