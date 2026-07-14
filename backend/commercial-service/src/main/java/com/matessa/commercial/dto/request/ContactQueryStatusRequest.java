package com.matessa.commercial.dto.request;

import com.matessa.commercial.entity.ContactQueryStatus;
import jakarta.validation.constraints.NotNull;

/** Staff action: move a query between OPEN and RESOLVED. */
public record ContactQueryStatusRequest(
        @NotNull ContactQueryStatus status
) {
}
