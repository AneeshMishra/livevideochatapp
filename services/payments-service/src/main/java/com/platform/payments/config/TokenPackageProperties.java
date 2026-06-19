package com.platform.payments.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class TokenPackageProperties {

    private List<TokenPackage> tokenPackages;

    @Getter
    @Setter
    public static class TokenPackage {
        private long id;
        private long tokens;
        private String price;   // string to avoid YAML float rounding (e.g. "9.99")
        private String currency;
        private String label;
    }
}
