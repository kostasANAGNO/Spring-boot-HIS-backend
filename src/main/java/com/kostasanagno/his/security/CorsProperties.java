package com.kostasanagno.his.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Origins (e.g. {@code http://localhost:3000}) that browsers may call the API from; empty = none. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
