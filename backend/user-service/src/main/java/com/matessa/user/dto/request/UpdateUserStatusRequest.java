package com.matessa.user.dto.request;

import jakarta.validation.constraints.NotNull;

/** Admin ban / un-ban: {@code enabled=false} blocks login and refresh. */
public record UpdateUserStatusRequest(
        @NotNull Boolean enabled
) {
}
