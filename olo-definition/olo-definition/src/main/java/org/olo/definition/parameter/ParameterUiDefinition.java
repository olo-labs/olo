/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.parameter;

import org.olo.spi.catalog.ParameterWidget;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Studio presentation hints for a workflow parameter ({@code parameters.*.ui}).
 */
@JsonDeserialize(builder = ParameterUiDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ParameterUiDefinition {

    private final String widget;
    private final String group;
    private final String help;
    private final String placeholder;
    private final Integer order;

    private ParameterUiDefinition(Builder builder) {
        this.widget = builder.widget;
        this.group = builder.group;
        this.help = builder.help;
        this.placeholder = builder.placeholder;
        this.order = builder.order;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getWidget() {
        return widget;
    }

    public String getGroup() {
        return group;
    }

    public String getHelp() {
        return help;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public Integer getOrder() {
        return order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParameterUiDefinition that)) {
            return false;
        }
        return Objects.equals(widget, that.widget)
                && Objects.equals(group, that.group)
                && Objects.equals(help, that.help)
                && Objects.equals(placeholder, that.placeholder)
                && Objects.equals(order, that.order);
    }

    @Override
    public int hashCode() {
        return Objects.hash(widget, group, help, placeholder, order);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String widget;
        private String group;
        private String help;
        private String placeholder;
        private Integer order;

        public Builder widget(String widget) {
            this.widget = widget == null ? null : ParameterWidget.normalizeCatalogValue(widget);
            return this;
        }

        public Builder widget(ParameterWidget widget) {
            this.widget = widget == null ? null : widget.catalogValue();
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder help(String help) {
            this.help = help;
            return this;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder order(Integer order) {
            this.order = order;
            return this;
        }

        public ParameterUiDefinition build() {
            return new ParameterUiDefinition(this);
        }
    }
}
