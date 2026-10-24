// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * {@code olo.toolgate.credentials.*}: how the backend obtains ToolGate user credentials for a run's user (M3.4).
 *
 * @param enabled             off by default; when off, the credential endpoint answers 404 and nothing calls ToolGate
 * @param controlUrl          ToolGate Control base URL, for example {@code https://control.example}
 * @param serviceTokenPath    file holding the backend's Control service JWT (role {@code toolgate-olo-token-exchange});
 *                            read on every call, so rotation needs no restart
 * @param assertionIssuer     {@code iss} of session assertions; ToolGate pins it
 * @param assertionAudience   {@code aud} of session assertions; ToolGate pins it
 * @param keyId               {@code kid} of the assertion key; ToolGate pins it
 * @param privateKeyPath      PKCS#8 PEM RSA key used only for session assertions
 * @param assertionLifetime   assertion lifetime; at most fifteen seconds to bound authority after failed revocation
 * @param requestTimeout      timeout for each ToolGate call
 */
@ConfigurationProperties(prefix = "olo.toolgate.credentials")
public record ToolGateCredentialProperties(Boolean enabled, URI controlUrl, Path serviceTokenPath, String assertionIssuer,
                                           String assertionAudience, String keyId, Path privateKeyPath,
                                           Duration assertionLifetime, Duration requestTimeout) {

    public ToolGateCredentialProperties {
        enabled = enabled != null && enabled;
        assertionLifetime = assertionLifetime == null ? Duration.ofSeconds(15) : assertionLifetime;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
    }

    /** Refuses an incomplete configuration when enabled. */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (controlUrl == null || !"https".equals(controlUrl.getScheme()) && !"localhost".equals(controlUrl.getHost())
                || serviceTokenPath == null || privateKeyPath == null || blank(assertionIssuer) || blank(assertionAudience)
                || blank(keyId) || !keyId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalStateException("olo.toolgate.credentials needs an https control-url, service-token-path, "
                    + "private-key-path, assertion-issuer, assertion-audience and key-id");
        }
        if (assertionLifetime.isNegative() || assertionLifetime.isZero() || assertionLifetime.compareTo(Duration.ofSeconds(15)) > 0) {
            throw new IllegalStateException("olo.toolgate.credentials.assertion-lifetime must be at most 15 seconds");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
