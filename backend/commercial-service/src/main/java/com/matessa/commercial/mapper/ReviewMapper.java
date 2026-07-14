package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.ReviewResponse;
import com.matessa.commercial.entity.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toResponse(Review review);
}
