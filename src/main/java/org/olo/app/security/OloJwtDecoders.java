/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JWT decoder OLO trusts. A token is accepted only if:
 * <ul>
 *   <li>its signature verifies against the issuer's JWKS, with an allowed algorithm (no {@code none}, no HMAC)</li>
 *   <li>{@code iss} equals the configured issuer exactly</li>
 *   <li>{@code aud} contains the configured audience</li>
 *   <li>{@code exp} is present and in the future, and {@code nbf} (if present) has passed, within the clock skew</li>
 *   <li>{@code sub} and the session claim are present</li>
 * </ul>
 */
public final class OloJwtDecoders {

    private OloJwtDecoders() {
    }

    public static JwtDecoder fromProperties(OloSecurityProperties properties) {
        properties.validate();
        NimbusJwtDecoder decoder = isBlank(properties.jwkSetUri())
                ? NimbusJwtDecoder.withIssuerLocation(properties.issuer()).jwsAlgorithms(algs -> addAlgorithms(algs, properties)).build()
                : NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).jwsAlgorithms(algs -> addAlgorithms(algs, properties)).build();
        decoder.setJwtValidator(validator(properties));
        return decoder;
    }

    /** The validator chain, also used with test decoders built from a fixed public key. */
    public static OAuth2TokenValidator<Jwt> validator(OloSecurityProperties properties) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(properties.clockSkew()));
        validators.add(new JwtIssuerValidator(properties.issuer()));
        validators.add(jwt -> jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : failure("invalid_audience", "token audience does not include " + properties.audience()));
        validators.add(jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : failure("missing_exp", "token has no exp"));
        validators.add(jwt -> !isBlank(jwt.getClaimAsString(JwtClaimNames.SUB))
                && !isBlank(jwt.getClaimAsString(properties.sessionClaim()))
                ? OAuth2TokenValidatorResult.success()
                : failure("missing_claims", "token must carry sub and " + properties.sessionClaim()));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static void addAlgorithms(java.util.Set<SignatureAlgorithm> target, OloSecurityProperties properties) {
        for (String name : properties.algorithms()) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.from(name);
            if (algorithm == null) {
                throw new IllegalStateException("olo.security.algorithms: unsupported or non-asymmetric algorithm " + name);
            }
            target.add(algorithm);
        }
    }

    private static OAuth2TokenValidatorResult failure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
