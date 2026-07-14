package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.CouponResponse;
import com.matessa.commercial.entity.Coupon;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    CouponResponse toResponse(Coupon coupon);
}
