# Olo

**Olo** helps you build chat applications that use AI in a structured way. It powers conversations, runs AI workflows (like search, planning, and approval steps), and streams results back to your app in real time.

**Source:** [github.com/OpenLLMOrchestrator/olo](https://github.com/OpenLLMOrchestrator/olo)  
**License:** [Apache License 2.0](LICENSE)

---

## What you get

- **Chat and conversations** — Create sessions, send messages, and keep history.
- **Structured AI runs** — Each message can trigger a workflow (e.g. plan → use tools → call a model → optional human approval).
- **Live updates** — Your app can listen for run events as they happen (streaming or over a single connection).
- **Multi-tenant** — Support many teams or customers with separate data and access.
- **APIs and docs** — REST APIs for everything; optional interactive docs for trying calls from the browser.
- **Run anywhere** — Use it locally, in Docker, or in your own cloud.

---

## Get started

1. Get the code and read the [demo guide](docs/DEMO.md) to run a full chat flow on your machine.
2. Use the [testing guide](docs/TESTING_WITH_SWAGGER.md) to try the APIs from your browser.
3. For Docker and deployment, see the [Docker](docs/DOCKER.md) and [Docker Compose](docs/DOCKER_COMPOSE.md) docs.

All technical details, configuration, and step-by-step instructions live in the **[docs](docs)** folder.

---

## Documentation

| Topic | Link |
|-------|------|
| Run the full demo (chat + AI workflow) | [docs/DEMO.md](docs/DEMO.md) |
| Test APIs with the browser | [docs/TESTING_WITH_SWAGGER.md](docs/TESTING_WITH_SWAGGER.md) |
| Build and publish the Docker image | [docs/DOCKER.md](docs/DOCKER.md) |
| Run with Docker Compose (dev / demo / prod) | [docs/DOCKER_COMPOSE.md](docs/DOCKER_COMPOSE.md) |
| Real-time run events (streaming) | [docs/WEBSOCKET.md](docs/WEBSOCKET.md) |
| Example API requests and responses | [docs/API_PAYLOADS.md](docs/API_PAYLOADS.md) |

---

## License

This project is licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) for the full text.
