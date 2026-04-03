/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowInputTest {

    private static final String SAMPLE_JSON = """
            {
              "version": "1.0",
              "inputs": [
                {
                  "name": "input1",
                  "displayName": "input1",
                  "type": "STRING",
                  "storage": {
                    "mode": "LOCAL"
                  },
                  "value": "Hi!"
                },
                {
                  "name": "input2",
                  "displayName": "input2",
                  "type": "STRING",
                  "storage": {
                    "mode": "CACHE",
                    "cache": {
                      "provider": "REDIS",
                      "key": "olo:worker:8huqpd42mizzgjOhJEH9C:input:input2"
                    }
                  }
                },
                {
                  "name": "input3",
                  "displayName": "input3",
                  "type": "FILE",
                  "storage": {
                    "mode": "LOCAL",
                    "file": {
                      "relativeFolder": "rag/8huqpd42mizzgjOhJEH9C/",
                      "fileName": "readme.md"
                    }
                  }
                }
              ],
              "context": {
                "tenantId": "",
                "groupId": "",
                "roles": ["PUBLIC", "ADMIN"],
                "permissions": ["STORAGE", "CACHE", "S3"],
                "sessionId": "<UUID>"
              },
              "routing": {
                "pipeline": "chat-queue-ollama",
                "transactionType": "QUESTION_ANSWER",
                "transactionId": "8huqpd42mizzgjOhJEH9C"
              },
              "metadata": {
                "ragTag": null,
                "timestamp": 1771740578582
              }
            }
            """;

    @Test
    void fromJson_parsesSample() {
        WorkflowInput input = WorkflowInput.fromJson(SAMPLE_JSON);

        assertEquals("1.0", input.getVersion());
        assertEquals(3, input.getInputs().size());

        InputItem i1 = input.getInputs().get(0);
        assertEquals("input1", i1.getName());
        assertEquals(InputType.STRING, i1.getType());
        assertEquals(StorageMode.LOCAL, i1.getStorage().getMode());
        assertEquals("Hi!", i1.getValue());

        InputItem i2 = input.getInputs().get(1);
        assertEquals("input2", i2.getName());
        assertEquals(StorageMode.CACHE, i2.getStorage().getMode());
        assertEquals(CacheProvider.REDIS, i2.getStorage().getCache().getProvider());
        assertEquals("olo:worker:8huqpd42mizzgjOhJEH9C:input:input2", i2.getStorage().getCache().getKey());

        InputItem i3 = input.getInputs().get(2);
        assertEquals("input3", i3.getName());
        assertEquals(InputType.FILE, i3.getType());
        assertEquals("rag/8huqpd42mizzgjOhJEH9C/", i3.getStorage().getFile().getRelativeFolder());
        assertEquals("readme.md", i3.getStorage().getFile().getFileName());

        assertEquals("chat-queue-ollama", input.getRouting().getPipeline());
        assertEquals(TransactionType.QUESTION_ANSWER, input.getRouting().getTransactionType());
        assertEquals("8huqpd42mizzgjOhJEH9C", input.getRouting().getTransactionId());

        assertEquals(1771740578582L, input.getMetadata().getTimestamp());
    }

    @Test
    void toJson_roundTrip() {
        WorkflowInput input = WorkflowInput.fromJson(SAMPLE_JSON);
        String json = input.toJson();
        assertNotNull(json);
        WorkflowInput parsed = WorkflowInput.fromJson(json);
        assertEquals(input.getVersion(), parsed.getVersion());
        assertEquals(input.getInputs().size(), parsed.getInputs().size());
        assertEquals(input.getRouting().getTransactionId(), parsed.getRouting().getTransactionId());
    }
}
