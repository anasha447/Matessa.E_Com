package com.matessa.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * Serialize {@link org.springframework.data.domain.Page} as a stable PagedModel DTO
 * (content + page metadata) instead of the unstable PageImpl shape, so every paginated
 * list endpoint returns a guaranteed JSON contract.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig {
}
