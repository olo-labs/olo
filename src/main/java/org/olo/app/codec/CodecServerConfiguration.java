/* Copyright (c) 2026 Olo Labs; SPDX-License-Identifier: Apache-2.0 */
package org.olo.app.codec;

import org.olo.input.codec.PayloadKeyRing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "olo.temporal.codec-server.enabled", havingValue = "true")
public class CodecServerConfiguration {
    @Bean("codecServerKeys")
    public PayloadKeyRing codecServerKeys(@Value("${olo.temporal.payload-keys-file:}") String file) throws IOException {
        if (file.isBlank()) throw new IllegalStateException("codec server requires a payload key file");
        return PayloadKeyRing.parse(Files.readString(Path.of(file)));
    }
}
