package edu.cit.pescante.mindhaven.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "mindhaven")
public class AppProperties {

    private Security security = new Security();
    private Cors cors = new Cors();
    private Bootstrap bootstrap = new Bootstrap();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public static class Security {
        private String jwtSecret;
        private long accessTokenTtlMinutes = 15;
        private long refreshTokenTtlDays = 7;
        private String emailHashPepper;
        private boolean cookieSecure = false;
        private int maxFailedAttempts = 5;
        private long lockoutMinutes = 15;
        private long passwordResetTtlMinutes = 15;
        private boolean exposeResetTokenInResponse = true;
        private String allowedEmailDomains = "cit.edu,edu,edu.ph";

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getAccessTokenTtlMinutes() {
            return accessTokenTtlMinutes;
        }

        public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
            this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        }

        public long getRefreshTokenTtlDays() {
            return refreshTokenTtlDays;
        }

        public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
            this.refreshTokenTtlDays = refreshTokenTtlDays;
        }

        public String getEmailHashPepper() {
            return emailHashPepper;
        }

        public void setEmailHashPepper(String emailHashPepper) {
            this.emailHashPepper = emailHashPepper;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public long getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(long lockoutMinutes) {
            this.lockoutMinutes = lockoutMinutes;
        }

        public long getPasswordResetTtlMinutes() {
            return passwordResetTtlMinutes;
        }

        public void setPasswordResetTtlMinutes(long passwordResetTtlMinutes) {
            this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        }

        public boolean isExposeResetTokenInResponse() {
            return exposeResetTokenInResponse;
        }

        public void setExposeResetTokenInResponse(boolean exposeResetTokenInResponse) {
            this.exposeResetTokenInResponse = exposeResetTokenInResponse;
        }

        public String getAllowedEmailDomains() {
            return allowedEmailDomains;
        }

        public void setAllowedEmailDomains(String allowedEmailDomains) {
            this.allowedEmailDomains = allowedEmailDomains;
        }

        public List<String> getAllowedEmailDomainList() {
            if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedEmailDomains.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedOriginList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of("http://localhost:5173");
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }

    public static class Bootstrap {
        private String adminEmail;
        private String adminPassword;
        private String adminFullName = "System Administrator";

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getAdminFullName() {
            return adminFullName;
        }

        public void setAdminFullName(String adminFullName) {
            this.adminFullName = adminFullName;
        }
    }
}
