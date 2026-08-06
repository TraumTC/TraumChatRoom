package com.tc.traumchatroom.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "jwt")
@Data
@Component
public class JwtProperties {
    private String secret;
    private long expiration = 604800000L;
}
