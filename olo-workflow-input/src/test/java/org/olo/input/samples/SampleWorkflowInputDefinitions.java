package org.olo.input.samples;

import org.olo.input.model.CacheProvider;
import org.olo.input.model.CacheStorage;
import org.olo.input.model.Context;
import org.olo.input.model.Execution;
import org.olo.input.model.FileStorage;
import org.olo.input.model.InputItem;
import org.olo.input.model.InputType;
import org.olo.input.model.Metadata;
import org.olo.input.model.Routing;
import org.olo.input.model.Storage;
import org.olo.input.model.StorageMode;
import org.olo.input.model.TransactionType;
import org.olo.input.model.WorkflowInput;
import org.olo.input.producer.CacheWriter;
import org.olo.input.producer.InputStorageKeys;
import org.olo.input.producer.WorkflowInputProducer;

import java.util.List;

/**
 * Programmatic builders mirroring workflow invocation payloads under {@code samples/}.
 */
public final class SampleWorkflowInputDefinitions {

    static final String TRANSACTION_ID = "8huqpd42mizzgjOhJEH9C";
    static final String TENANT_ID = "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d";

    private SampleWorkflowInputDefinitions() {
    }

    public static WorkflowInput minimalLocal() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "query",
                        "Query",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "Hello, OLO"))
                .routing(new Routing("default-pipeline", TransactionType.QUESTION_ANSWER, TRANSACTION_ID))
                .build();
    }

    /** LOCAL string, CACHE (Redis), and FILE reference in one payload. */
    public static WorkflowInput mixedStorage() {
        String cacheKey = InputStorageKeys.cacheKey(TENANT_ID, TRANSACTION_ID, "input2");
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "input1",
                        "input1",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "Hi!"))
                .addInput(new InputItem(
                        "input2",
                        "input2",
                        InputType.STRING,
                        new Storage(StorageMode.CACHE, new CacheStorage(CacheProvider.REDIS, cacheKey), null),
                        null))
                .addInput(new InputItem(
                        "input3",
                        "input3",
                        InputType.FILE,
                        new Storage(
                                StorageMode.LOCAL,
                                null,
                                new FileStorage("rag/" + TRANSACTION_ID + "/", "readme.md")),
                        null))
                .context(new Context(
                        "",
                        "",
                        List.of("PUBLIC", "ADMIN"),
                        List.of("STORAGE", "CACHE", "S3"),
                        "<UUID>"))
                .routing(new Routing("chat-queue-ollama", TransactionType.QUESTION_ANSWER, TRANSACTION_ID))
                .metadata(new Metadata(null, 1_771_740_578_582L))
                .build();
    }

    /** Producer API: inline small strings, offload large strings to cache. */
    public static WorkflowInput producerOffload() {
        CacheWriter cacheWriter = (key, value) -> { /* sample: no-op store */ };
        return WorkflowInputProducer.create(100, cacheWriter, TRANSACTION_ID, "1.0")
                .context(new Context(TENANT_ID, "finance", List.of("USER"), List.of("CACHE"), "sess-producer"))
                .routing(new Routing("chat-queue", TransactionType.QUESTION_ANSWER, TRANSACTION_ID))
                .addStringInput("prompt", "Prompt", "Analyze INFY")
                .addStringInput("context", "Context", "x".repeat(200))
                .build();
    }

    public static WorkflowInput cacheInMemory() {
        String cacheKey = InputStorageKeys.cacheKey(TENANT_ID, TRANSACTION_ID, "sessionState");
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "sessionState",
                        "Session state",
                        InputType.JSON,
                        new Storage(StorageMode.CACHE, new CacheStorage(CacheProvider.IN_MEMORY, cacheKey), null),
                        null))
                .context(new Context(TENANT_ID, "dev", List.of("USER"), List.of("CACHE"), "sess-mem"))
                .routing(new Routing("dev-pipeline", TransactionType.QUESTION_ANSWER, TRANSACTION_ID))
                .build();
    }

    public static WorkflowInput typedInputs() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "symbol",
                        "Symbol",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "INFY"))
                .addInput(new InputItem(
                        "quantity",
                        "Quantity",
                        InputType.NUMBER,
                        new Storage(StorageMode.LOCAL, null, null),
                        "100"))
                .addInput(new InputItem(
                        "dryRun",
                        "Dry run",
                        InputType.BOOLEAN,
                        new Storage(StorageMode.LOCAL, null, null),
                        "true"))
                .addInput(new InputItem(
                        "filters",
                        "Filters",
                        InputType.JSON,
                        new Storage(StorageMode.LOCAL, null, null),
                        "{\"sector\":\"IT\",\"minVolume\":1000000}"))
                .addInput(new InputItem(
                        "profile",
                        "Profile",
                        InputType.OBJECT,
                        new Storage(StorageMode.LOCAL, null, null),
                        "{\"risk\":\"moderate\",\"horizon\":\"long\"}"))
                .routing(new Routing("stock-analysis", TransactionType.WORKFLOW_RUN, TRANSACTION_ID))
                .build();
    }

    public static WorkflowInput agentExecution() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "task",
                        "Task",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "Summarize the latest earnings call"))
                .context(new Context(
                        TENANT_ID,
                        "ops",
                        List.of("ADMIN"),
                        List.of("STORAGE", "CACHE"),
                        "sess-agent",
                        "run-001",
                        null,
                        "corr-xyz"))
                .execution(new Execution("https://api.example.com/callback", 300))
                .routing(new Routing("agent-pipeline", TransactionType.AGENT_EXECUTION, TRANSACTION_ID))
                .build();
    }

    public static WorkflowInput workflowRun() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "symbol",
                        "Symbol",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "AAPL"))
                .addInput(new InputItem(
                        "question",
                        "Question",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "What is the short-term outlook?"))
                .context(new Context(TENANT_ID, "research", List.of("ANALYST"), List.of("STORAGE"), "sess-wf"))
                .execution(new Execution(null, 300))
                .routing(new Routing(
                        "stock-analysis",
                        TransactionType.WORKFLOW_RUN,
                        TRANSACTION_ID,
                        "1.0.0"))
                .build();
    }

    /** Remote storage modes supported by the schema (value resolved at runtime). */
    public static WorkflowInput storageRemote() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "report",
                        "Report",
                        InputType.FILE,
                        new Storage(StorageMode.S3, null, null),
                        null))
                .addInput(new InputItem(
                        "auditId",
                        "Audit id",
                        InputType.STRING,
                        new Storage(StorageMode.DB, null, null),
                        null))
                .routing(new Routing("compliance-pipeline", TransactionType.WORKFLOW_RUN, TRANSACTION_ID))
                .build();
    }

    public static WorkflowInput ragMetadata() {
        return WorkflowInput.builder()
                .version("1.0")
                .addInput(new InputItem(
                        "question",
                        "Question",
                        InputType.STRING,
                        new Storage(StorageMode.LOCAL, null, null),
                        "Explain the dividend policy"))
                .context(new Context(TENANT_ID, "finance", List.of("USER"), List.of("STORAGE"), "sess-rag"))
                .routing(new Routing("rag-chat", TransactionType.QUESTION_ANSWER, TRANSACTION_ID))
                .metadata(new Metadata("finance-rag-v2", 1_771_740_578_582L))
                .build();
    }
}
