package dev.devatlas.server.auth;

import dev.devatlas.server.auth.dto.UserResponse;
import dev.devatlas.server.auth.dto.UserSummaryResponse;
import dev.devatlas.server.domain.User;
import org.mapstruct.Mapper;

/**
 * Entity to response mapping for accounts.
 *
 * <p>Generated rather than hand-written so that adding a field to the entity does not silently
 * leave the response behind, and so that the boundary stays a boundary: an entity never reaches a
 * controller, and a mapper is the only thing that knows both sides.
 *
 * <p>Note what is not mapped: {@code passwordHash} and {@code enabled} have no counterpart in
 * either response type, so neither can leak through this layer by accident.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

  UserSummaryResponse toSummary(User user);

  UserResponse toResponse(User user);
}
