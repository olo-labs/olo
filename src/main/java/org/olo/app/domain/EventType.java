/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.domain;

/**
 * Explicit event type for analytics and filtering. Prefer over deriving from nodeType + status.
 */
public enum EventType {
    NODE_STARTED,
    NODE_COMPLETED,
    NODE_FAILED,
    NODE_WAITING
}
