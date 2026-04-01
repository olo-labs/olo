# olo-worker-input

**olo-worker-input** is there to **serialize and deserialize** workflow input for OLO Temporal workflows. It provides the workflow input model (WorkflowInput, inputs, context, routing, metadata), JSON serialization/deserialization, and a consumer/producer abstraction for reading and building payloads.

## Package structure (by responsibility)

| Package | Responsibility | Main types |
|---------|-----------------|------------|
| **`org.olo.input.model`** | Payload DTOs and enums; JSON (de)serialization; fluent builder | `WorkflowInput`, `WorkflowInputBuilder` (use `WorkflowInput.builder()`), `InputItem`, `Storage`, `Context`, `Routing`, `Metadata`, `InputType`, `StorageMode`, `CacheProvider`, `TransactionType` |
| **`org.olo.input.consumer`** | Read-only contract and resolution | `WorkflowInputValues`, `DefaultWorkflowInputValues`, `CacheReader`, `FileReader` |
| **`org.olo.input.producer`** | Building payloads and writing to cache | `WorkflowInputProducer`, `CacheWriter`, `InputStorageKeys` |
| **`org.olo.input.config`** | Configuration from environment | `MaxLocalMessageSize` |

## Contract: consumer vs producer

- **Consumer (workflow/worker)**: gets a **read-only** view of inputs. They can only **consume** values (e.g. `getStringValue("input1")`). They cannot change the payload. Where the value lives (inline, Redis, file) is hidden.
- **Producer (workflow starter)**: has **full access**. They build the payload, set values, and decide storage. When a string value exceeds the max local size, the producer stores it in Redis and shares the key in the payload.

## Environment variable

| Variable | Description | Default |
|----------|-------------|---------|
| `OLO_MAX_LOCAL_MESSAGE_SIZE` | Max size (characters) for inline LOCAL string values. Larger values are stored in cache (e.g. Redis) and the key is put in the payload. | `50` |

Also supported in `olo-worker-configuration` via `OloConfig.getMaxLocalMessageSize()`.

---

## Producer: sending the workflow creation request

The producer builds a `WorkflowInput` and sends its JSON as the workflow input. Use `WorkflowInputProducer` so that values over `OLO_MAX_LOCAL_MESSAGE_SIZE` are automatically stored in Redis and the cache key is shared.

### 1. Implement `CacheWriter`

You provide the Redis (or other cache) write implementation:

```java
import org.olo.input.producer.CacheWriter;

CacheWriter cacheWriter = (key, value) -> redisClient.set(key, value);

// or with a concrete class
public class RedisCacheWriter implements CacheWriter {
    private final RedisClient redis;
    @Override
    public void put(String key, String value) {
        redis.set(key, value);
    }
}
```

### 2. Build the payload with `WorkflowInputProducer`

Use `MaxLocalMessageSize.fromEnvironment()` so the limit comes from `OLO_MAX_LOCAL_MESSAGE_SIZE`:

```java
import org.olo.input.config.MaxLocalMessageSize;
import org.olo.input.model.*;
import org.olo.input.producer.WorkflowInputProducer;

int maxLocal = MaxLocalMessageSize.fromEnvironment(); // or OloConfig.fromEnvironment().getMaxLocalMessageSize()
CacheWriter cacheWriter = new RedisCacheWriter(redis);
String transactionId = "8huqpd42mizzgjOhJEH9C";

WorkflowInput input = WorkflowInputProducer
    .create(maxLocal, cacheWriter, transactionId, "1.0")
    .context(new Context("", "", List.of("PUBLIC", "ADMIN"), List.of("STORAGE", "CACHE", "S3"), "<UUID>"))
    .routing(new Routing("chat-queue-ollama", TransactionType.QUESTION_ANSWER, transactionId))
    .metadata(new Metadata(null, System.currentTimeMillis()))
    .addStringInput("input1", "input1", "Hi!")                                    // inline (small)
    .addStringInput("input2", "input2", veryLongString)                           // stored in Redis, key in payload
    .addFileInput("input3", "input3", "rag/8huqpd42mizzgjOhJEH9C/", "readme.md")
    .build();
```

- If `value.length() <= maxLocalMessageSize` → input is stored as **LOCAL** with inline `value`.
- If `value.length() > maxLocalMessageSize` → value is written via `CacheWriter.put(key, value)` and the input is stored as **CACHE** with key `olo:worker:{transactionId}:input:{name}`.

### 3. Send the JSON to Temporal

Serialize and pass as the workflow input argument:

```java
String json = input.toJson();
// Start workflow with json as input (e.g. Temporal workflow client)
workflowClient.start(WorkflowInput::fromJson, json);
```

---

## Consumer (workflow): deserializing and using the input

The workflow receives the same JSON string. Deserialize once, then use the **consumer contract** so activities/workflows only read values by name. They do not care whether a value came from LOCAL, CACHE, or FILE.

### 1. Deserialize the message

```java
import org.olo.input.model.WorkflowInput;

String rawInput = workflow.getInput(); // or activity input
WorkflowInput input = WorkflowInput.fromJson(rawInput);
```

### 2. Implement `CacheReader` and `FileReader`

You provide how to read from cache and from files (e.g. Redis + local/S3):

```java
import org.olo.input.consumer.CacheReader;
import org.olo.input.consumer.FileReader;

CacheReader cacheReader = key -> Optional.ofNullable(redis.get(key));
FileReader fileReader = new FileReader() {
    @Override
    public Optional<String> readAsString(String relativeFolder, String fileName) {
        Path path = baseDir.resolve(relativeFolder).resolve(fileName);
        return Files.exists(path) ? Optional.of(Files.readString(path)) : Optional.empty();
    }
    @Override
    public Optional<byte[]> readAsBytes(String relativeFolder, String fileName) {
        Path path = baseDir.resolve(relativeFolder).resolve(fileName);
        return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
    }
};
```

### 3. Expose the read-only contract to workflow/activities

Build `WorkflowInputValues` (the consumer contract) and pass it to your workflow/activity code. Callers only call getters; they cannot modify the payload.

```java
import org.olo.input.consumer.DefaultWorkflowInputValues;
import org.olo.input.consumer.WorkflowInputValues;

WorkflowInputValues values = new DefaultWorkflowInputValues(input, cacheReader, fileReader);

// In your activity or workflow logic:
Optional<String> greeting = values.getStringValue("input1");   // "Hi!" (from LOCAL or CACHE)
Optional<String> big    = values.getStringValue("input2");     // from Redis, key was in payload
Optional<String> file   = values.getFileContentAsString("input3"); // file content

String input1 = values.getStringValue("input1").orElse("");
```

The consumer never touches Redis or file paths directly; they only use `getStringValue("input1")`, etc. Storage is abstracted.

---

## Summary

| Role | Access | Packages / types |
|------|--------|-------------------|
| **Producer** | Read + write: build payload, set values, decide LOCAL vs CACHE when over max size | `org.olo.input.producer`: `WorkflowInputProducer`, `CacheWriter`; `org.olo.input.model`: `WorkflowInput.toJson()` |
| **Consumer** | Read-only: get values by name, no storage details | `org.olo.input.consumer`: `WorkflowInputValues`, `DefaultWorkflowInputValues`, `CacheReader`, `FileReader` |

- **Key format** for cache: `olo:worker:{transactionId}:input:{inputName}` (see `org.olo.input.producer.InputStorageKeys.cacheKey()`).
- **Max local size**: `OLO_MAX_LOCAL_MESSAGE_SIZE` (default 50). Larger strings are stored in Redis and the key is shared in the payload; the consumer still uses `getStringValue("input1")` and gets the value back.
