<!--
Copyright (c) 2026 Olo Labs
SPDX-License-Identifier: Apache-2.0
-->
# olo-mono

Monorepo for **OLO** (AI orchestration): declarative workflow definitions separated from runtime execution.

## Documentation

See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for module boundaries, graph model, serialization, and planned runtime design.

Example workflows: [samples/](samples/). Preset chat/task-queue definitions: [olo-configuration/](olo-configuration/).

## Modules

| Module | Status | Description |
|--------|--------|-------------|
| [olo-definition](olo-definition/) | Active | Serializable workflow graph POJOs, builders, JSON/YAML |
| olo-runtime | Planned | Workflow execution engine |
| olo-extensions | Planned | OpenAI, Ollama, Temporal, MCP, vector stores, tools |

## Requirements

- Java 17+
- Gradle 8.12+ (wrapper included)

## Build

```bash
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

The build generates workflow samples under `samples/` (see `gradlew generateSamples`).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
