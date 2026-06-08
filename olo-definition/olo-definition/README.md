# olo-definition

Pure Java library for **serializable AI orchestration workflow graphs**. No execution, no model calls, no runtime state, no Spring.

## Build

From the monorepo root (Java 17+):

```bash
./gradlew :olo-definition:build
```

## Usage

### Fluent builder

```java
WorkflowDefinition workflow = WorkflowBuilder.create("Stock Workflow")
    .id("stock-analysis")
    .inputNode("request")
    .modelNode("analysis", "CHAT")
    .toolNode("screener")
    .outputNode("response")
    .connect("request", "analysis")
    .connect("analysis", "screener")
    .connect("screener", "response")
    .build();

WorkflowValidator.validateOrThrow(workflow);
```

### Dynamic extension

```java
WorkflowDefinition enhanced = WorkflowBuilder.from(baseWorkflow)
    .addNode(ragNode)
    .connect("input", "rag1")
    .build();
```

### JSON / YAML

```java
JsonWorkflowSerializer json = new JsonWorkflowSerializer();
String text = json.serialize(workflow);
WorkflowDefinition loaded = json.deserialize(text);

YamlWorkflowSerializer yaml = new YamlWorkflowSerializer();
```

## Module boundaries

| Module           | Responsibility                          |
|------------------|-----------------------------------------|
| `olo-definition` | Declarative workflow POJOs + serializers  |
| `olo-runtime`    | Execution (future; depends on definition) |
| `olo-extensions` | Provider integrations (future)          |

`olo-definition` must never depend on `olo-runtime`.

## Package layout

- `org.olo.definition.workflow` — `WorkflowDefinition`, `WorkflowBuilder`
- `org.olo.definition.node` — `NodeDefinition`, `NodeRouterDefinition`, `NodeType`
- `org.olo.definition.edge` — `EdgeDefinition`
- `org.olo.definition.variable` — `VariableDefinition`
- `org.olo.definition.model` — `ModelProviderDefinition`, `ModelRoutingDefinition`
- `org.olo.definition.extension` — `ExtensionDefinition`
- `org.olo.definition.serializer` — JSON/YAML serializers
- `org.olo.definition.validation` — `WorkflowValidator`
