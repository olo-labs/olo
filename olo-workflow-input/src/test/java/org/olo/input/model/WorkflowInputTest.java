package org.olo.input.model;

import org.olo.input.samples.SampleWorkflowInputDefinitions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowInputTest {

    @Test
    void fromJson_parsesMixedStorageSample() {
        WorkflowInput input = SampleWorkflowInputDefinitions.mixedStorage();

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

        InputItem i3 = input.getInputs().get(2);
        assertEquals("input3", i3.getName());
        assertEquals(InputType.FILE, i3.getType());
        assertEquals("rag/8huqpd42mizzgjOhJEH9C/", i3.getStorage().getFile().getRelativeFolder());
        assertEquals("readme.md", i3.getStorage().getFile().getFileName());

        assertEquals("chat-queue-ollama", input.getRouting().getPipeline());
        assertEquals(TransactionType.QUESTION_ANSWER, input.getRouting().getTransactionType());
        assertEquals("8huqpd42mizzgjOhJEH9C", input.getRouting().getTransactionId());

        assertEquals(1_771_740_578_582L, input.getMetadata().getTimestamp());
    }

    @Test
    void copyProducesIndependentImmutableWorkflowInput() {
        WorkflowInput original = SampleWorkflowInputDefinitions.mixedStorage();

        WorkflowInput copied = original.copy();
        WorkflowInput copiedViaStatic = WorkflowInput.copyOf(original);

        assertThat(copied).isEqualTo(original).isNotSameAs(original);
        assertThat(copiedViaStatic).isEqualTo(original).isNotSameAs(original);

        WorkflowInput modified = original.toBuilder()
                .version("2.0")
                .build();
        assertThat(original.getVersion()).isEqualTo("1.0");
        assertThat(modified.getVersion()).isEqualTo("2.0");
        assertThat(modified.getInputs()).isEqualTo(original.getInputs());
    }

    @Test
    void toJson_roundTrip() {
        WorkflowInput input = SampleWorkflowInputDefinitions.mixedStorage();
        String json = input.toJson();
        assertNotNull(json);
        WorkflowInput parsed = WorkflowInput.fromJson(json);
        assertEquals(input, parsed);
    }

    @Test
    void fromJson_parsesExecutionBlock() {
        String json = """
                {
                  "version": "1.0",
                  "inputs": [],
                  "routing": {
                    "pipeline": "agent-pipeline",
                    "transactionType": "AGENT_EXECUTION",
                    "transactionId": "tx-1"
                  },
                  "execution": {
                    "callbackUrl": "https://api.example.com/callback",
                    "timeoutSeconds": 300
                  }
                }
                """;

        WorkflowInput input = WorkflowInput.fromJson(json);

        assertNotNull(input.getExecution());
        assertEquals("https://api.example.com/callback", input.getExecution().getCallbackUrl());
        assertEquals(300, input.getExecution().getTimeoutSeconds());
    }
}
