package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.ProductImageResponse;
import com.matessa.commercial.entity.ProductImage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    ProductImageResponse toResponse(ProductImage image);
}
