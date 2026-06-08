/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory registry of regional configuration loaded from {@code olo.configuration.dir}.
 */
@Component
public class RegionalConfigurationRegistry {

    private volatile Map<String, RegionalConfigurationSnapshot> byRegion = Map.of();
    private volatile String defaultRegion = "default";

    public void replaceAll(Map<String, RegionalConfigurationSnapshot> snapshots, String defaultRegion) {
        this.byRegion = snapshots == null || snapshots.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(snapshots));
        if (defaultRegion != null && !defaultRegion.isBlank()) {
            this.defaultRegion = defaultRegion.trim();
        }
    }

    public List<String> getRegions() {
        return List.copyOf(byRegion.keySet());
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public RegionalConfigurationSnapshot get(String region) {
        if (region == null || region.isBlank()) {
            return byRegion.get(defaultRegion);
        }
        RegionalConfigurationSnapshot snap = byRegion.get(region.trim());
        if (snap != null) {
            return snap;
        }
        return byRegion.get(defaultRegion);
    }

    public boolean isLoaded() {
        return !byRegion.isEmpty();
    }
}
