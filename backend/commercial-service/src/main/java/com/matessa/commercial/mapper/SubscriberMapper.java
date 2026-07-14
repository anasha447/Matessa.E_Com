package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.SubscriberResponse;
import com.matessa.commercial.entity.Subscriber;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriberMapper {

    SubscriberResponse toResponse(Subscriber subscriber);
}
