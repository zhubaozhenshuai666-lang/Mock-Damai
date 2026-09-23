package com.zewbby.smartticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart-ticket.audience-identity")
public class AudienceIdentityProperties {

    /**
     * 用于观演人证件号加密和哈希的独立密钥。生产环境必须通过环境变量注入。
     */
    private String encryptionSecret = "smart-ticket-audience-identity-secret-at-least-32-bytes";
}
