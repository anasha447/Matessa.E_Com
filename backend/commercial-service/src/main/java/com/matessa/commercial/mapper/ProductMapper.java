package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.ProductResponse;
import com.matessa.commercial.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);
}
