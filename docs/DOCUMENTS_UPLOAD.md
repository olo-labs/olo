# Document upload (Chat BE)

<!--
Copyright (c) 2026 Olo Labs
SPDX-License-Identifier: Apache-2.0
-->

This describes how **resource / document upload** is intended to work in the Olo stack: **ingest is always handled by the backend**; **object storage is pluggable**.

## API (olo-chat ↔ Chat BE)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/resource/upload` | Multipart: **`capabilitySource`** (capability source id), **`files`**, optional **`taskQueue`**, **`pipelineId`**. |
| `POST` | `/api/resource/reprocess` | JSON: **`capabilitySource`**, **`fileName`**. Optional: re-trigger downstream indexing/workflow. |

Legacy **`/api/rag/upload`** and **`/api/rag/reprocess`** with form/body field **`ragId`** are **deprecated**; new implementations should use the **`resource`** paths and **`capabilitySource`**.

## Responsibilities

| Layer | Role |
|-------|------|
| **olo-chat** | Sends `multipart/form-data` to the Chat BE (`POST /api/resource/upload`). No direct writes to disk or cloud storage. |
| **Chat BE** | Accepts upload, authenticates/authorizes, validates size/type, **persists bytes** via a storage abstraction, returns structured response (e.g. `runId` for optional downstream workflows). |

## Storage abstraction

Persisted content should not be hard-coded to “a path on the app server” in the long term. Implement a **storage port** (interface) inside the backend, for example:

- **Local filesystem** — write under a configurable **shared folder** (current typical default for single-node or dev).
- **Object storage** — **Amazon S3**, **Azure Blob Storage**, **Google Cloud Storage**, **MinIO**, etc., for horizontal scale and durability.

The **HTTP contract** (`capabilitySource`, files, optional queue/pipeline) can stay stable while the **adapter** behind it switches from local disk to S3/Blob.

## Capability source

The multipart field **`capabilitySource`** identifies the **capability source** (logical bucket/prefix for that tenant’s material). This upload **does not** run retrieval/indexing; **capabilities** and indexing workflows consume that source later.

## Troubleshooting (backend logs)

Set loggers to **INFO** (defaults in `application.properties`):

- **`org.olo.app.service.ResourceUploadService`** — each upload: client IP, optional `X-Request-Id`, `capabilitySource`, part count, per-file name/size/content-type, target directory, save path, or stack trace on failure.
- **`org.olo.app.controller.ResourceUploadController`** — request entry for `/api/resource/upload` (Content-Type, Content-Length).
- **`org.olo.app.controller.ResourceUploadExceptionHandler`** — **413** (max upload size) and other **multipart** errors with full exception.
- **`org.olo.app.filter.RequestLoggingFilter`** — skips multipart body bytes (logs `multipart omitted`); use the classes above instead.

Upload directory: **`olo.resource.upload.base-dir`** (default: `{java.io.tmpdir}/olo-resource-uploads`). Override with **`OLO_RESOURCE_UPLOAD_BASE_DIR`**.

## Related frontend

See **olo-chat** `api/documentsUploadApi.ts` and Documents UI copy: uploads are BE-owned; local folder vs cloud is a deployment choice. Optional env: **`VITE_RESOURCE_UPLOAD_QUEUE`**, **`VITE_RESOURCE_UPLOAD_PIPELINE`** (with fallback to legacy **`VITE_RAG_QUEUE`**, **`VITE_RAG_PIPELINE`**). Error responses that include JSON **`message`** are shown in the upload table.
