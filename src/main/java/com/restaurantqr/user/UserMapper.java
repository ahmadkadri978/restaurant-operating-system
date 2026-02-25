package com.restaurantqr.user;

import com.restaurantqr.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "restaurantId", expression = "java(user.getRestaurant() != null ? user.getRestaurant().getId() : null)")
    @Mapping(target = "active", source = "active")
    UserResponse toResponse(User user);
}
