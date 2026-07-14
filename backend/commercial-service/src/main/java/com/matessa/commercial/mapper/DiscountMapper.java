package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.DiscountResponse;
import com.matessa.commercial.entity.Discount;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    DiscountResponse toResponse(Discount discount);
}
