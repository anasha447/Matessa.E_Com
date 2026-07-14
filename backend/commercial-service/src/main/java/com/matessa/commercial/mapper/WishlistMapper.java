package com.matessa.commercial.mapper;

import com.matessa.commercial.dto.response.WishlistItemResponse;
import com.matessa.commercial.entity.WishlistItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WishlistMapper {

    WishlistItemResponse toResponse(WishlistItem item);
}
