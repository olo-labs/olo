/* Copyright (c) 2026 Olo Labs; SPDX-License-Identifier: Apache-2.0 */
package org.olo.app.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.payload.context.WorkflowSerializationContext;
import org.olo.app.security.CurrentPrincipal;
import org.olo.input.codec.OloPayloadCodec;
import org.olo.input.codec.PayloadKeyRing;
import org.olo.input.codec.TenantWorkflowIds;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporal remote-codec JSON endpoints; operators can only decrypt their authenticated tenant's payloads. */
@RestController
@RequestMapping("/api/codec")
@ConditionalOnProperty(name = "olo.temporal.codec-server.enabled", havingValue = "true")
public class CodecServerController {
    private static final int MAX_BATCH = 100;
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private final OloPayloadCodec codec;

    public CodecServerController(@Qualifier("codecServerKeys") PayloadKeyRing keys) {
        codec = new OloPayloadCodec(keys);
    }

    @PostMapping("/decode")
    public ResponseEntity<Map<String, Object>> decode(@RequestBody JsonNode request) { return transform(request, false); }

    @PostMapping("/encode")
    public ResponseEntity<Map<String, Object>> encode(@RequestBody JsonNode request) { return transform(request, true); }

    private ResponseEntity<Map<String, Object>> transform(JsonNode request, boolean encode) {
        String tenant = CurrentPrincipal.requireAdmin().tenantId();
        JsonNode items = request.path("payloads");
        if (!items.isArray() || items.size() > MAX_BATCH) throw invalid();
        List<Payload> payloads = new ArrayList<>();
        long bytes = 0;
        try {
            for (JsonNode item : items) {
                if (!item.isObject() || !item.path("metadata").isObject() || !item.path("data").isTextual()) throw invalid();
                Payload.Builder payload = Payload.newBuilder();
                var metadata = item.path("metadata").fields();
                while (metadata.hasNext()) {
                    var field = metadata.next();
                    if (!field.getValue().isTextual()) throw invalid();
                    bytes += field.getKey().length() + field.getValue().textValue().length();
                    if (bytes > MAX_BYTES) throw invalid();
                    payload.putMetadata(field.getKey(), ByteString.copyFrom(Base64.getDecoder().decode(field.getValue().textValue())));
                }
                bytes += item.get("data").textValue().length();
                if (bytes > MAX_BYTES) throw invalid();
                payload.setData(ByteString.copyFrom(Base64.getDecoder().decode(item.get("data").textValue())));
                Payload built = payload.build();
                if (!encode && !tenant.equals(built.getMetadataOrDefault("olo-tenant", ByteString.EMPTY).toStringUtf8())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "payload is outside the authenticated tenant");
                }
                payloads.add(built);
            }
            var scoped = codec.withContext(new WorkflowSerializationContext("codec-server", TenantWorkflowIds.run(tenant, "codec")));
            List<Map<String, Object>> output = new ArrayList<>();
            for (Payload result : encode ? scoped.encode(payloads) : scoped.decode(payloads)) {
                Map<String, String> metadata = new LinkedHashMap<>();
                result.getMetadataMap().forEach((key, value) -> metadata.put(key, Base64.getEncoder().encodeToString(value.toByteArray())));
                output.add(Map.of("metadata", metadata, "data", Base64.getEncoder().encodeToString(result.getData().toByteArray())));
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("payloads", output));
        } catch (IllegalArgumentException | io.temporal.payload.codec.PayloadCodecException e) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid codec payload request");
    }
}
