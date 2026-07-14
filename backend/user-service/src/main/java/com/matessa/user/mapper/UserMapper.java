package com.matessa.user.mapper;

import com.matessa.user.dto.response.AdminUserResponse;
import com.matessa.user.dto.response.UserResponse;
import com.matessa.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    AdminUserResponse toAdminResponse(User user);
}
