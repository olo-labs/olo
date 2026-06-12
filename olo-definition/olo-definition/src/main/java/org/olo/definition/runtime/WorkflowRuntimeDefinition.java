package org.olo.definition.runtime;



import com.fasterxml.jackson.annotation.JsonAlias;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import org.olo.definition.execution.ExecutionModel;

import org.olo.spi.runtime.RuntimeCapability;



import java.time.Duration;

import java.util.ArrayList;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Objects;

import java.util.Set;



/**

 * Workflow-level orchestration contract ({@code runtime} on {@link org.olo.definition.workflow.WorkflowDefinition}).

 * <p>

 * Mirrors catalog {@code runtime} shape: {@code contractVersion}, {@code executionModel}, {@code capabilities},

 * optional {@code defaultTimeout}, and agent {@code delegation} policy when applicable.

 */

@JsonDeserialize(builder = WorkflowRuntimeDefinition.Builder.class)

@JsonInclude(JsonInclude.Include.NON_NULL)

@JsonIgnoreProperties(ignoreUnknown = true)

@JsonPropertyOrder({"contractVersion", "executionModel", "capabilities", "defaultTimeout", "delegation"})

public final class WorkflowRuntimeDefinition {



    public static final String DEFAULT_CONTRACT_VERSION = "1.0";



    private final String contractVersion;

    private final ExecutionModel executionModel;

    private final List<RuntimeCapability> capabilities;

    private final String defaultTimeout;

    private final RuntimeDelegationDefinition delegation;



    private WorkflowRuntimeDefinition(Builder builder) {

        this.contractVersion = builder.contractVersion == null ? DEFAULT_CONTRACT_VERSION : builder.contractVersion;

        this.executionModel = builder.executionModel;

        this.capabilities = builder.capabilities == null

                ? List.of()

                : List.copyOf(builder.capabilities);

        this.defaultTimeout = builder.defaultTimeout;

        this.delegation = builder.delegation;

    }



    public static Builder builder() {

        return new Builder();

    }



    public String getContractVersion() {

        return contractVersion;

    }



    public ExecutionModel getExecutionModel() {

        return executionModel;

    }



    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<RuntimeCapability> getCapabilities() {

        return capabilities;

    }



    public String getDefaultTimeout() {

        return defaultTimeout;

    }



    public RuntimeDelegationDefinition getDelegation() {

        return delegation;

    }



    public static void validate(WorkflowRuntimeDefinition runtime, String context, List<String> errors) {

        if (runtime == null) {

            return;

        }

        validateDefaultTimeout(runtime.getDefaultTimeout(), context, errors);

        RuntimeDelegationDefinition.validate(runtime.getDelegation(), context, errors);

    }



    @JsonIgnore

    public boolean isDebuggable() {

        return capabilities.contains(RuntimeCapability.DEBUG);

    }



    @JsonIgnore

    public boolean isReplayable() {

        return capabilities.contains(RuntimeCapability.REPLAY);

    }



    @JsonIgnore

    public boolean isTimeoutAware() {

        return capabilities.contains(RuntimeCapability.TIMEOUT);

    }



    /** Validates {@code defaultTimeout} when set; no-op when blank. */

    public static void validateDefaultTimeout(String defaultTimeout, String context, List<String> errors) {

        if (defaultTimeout == null || defaultTimeout.isBlank()) {

            return;

        }

        try {

            Duration.parse(defaultTimeout.trim());

        } catch (Exception e) {

            errors.add(context + ": runtime.defaultTimeout must be a valid ISO-8601 duration");

        }

    }



    @Override

    public boolean equals(Object o) {

        if (this == o) {

            return true;

        }

        if (!(o instanceof WorkflowRuntimeDefinition that)) {

            return false;

        }

        return Objects.equals(contractVersion, that.contractVersion)

                && executionModel == that.executionModel

                && Objects.equals(capabilities, that.capabilities)

                && Objects.equals(defaultTimeout, that.defaultTimeout)

                && Objects.equals(delegation, that.delegation);

    }



    @Override

    public int hashCode() {

        return Objects.hash(contractVersion, executionModel, capabilities, defaultTimeout, delegation);

    }



    @JsonPOJOBuilder(withPrefix = "")

    public static final class Builder {



        @JsonAlias("apiVersion")

        private String contractVersion;

        private ExecutionModel executionModel;

        private List<RuntimeCapability> capabilities;

        private String defaultTimeout;

        private RuntimeDelegationDefinition delegation;



        public Builder contractVersion(String contractVersion) {

            this.contractVersion = contractVersion;

            return this;

        }



        public Builder executionModel(ExecutionModel executionModel) {

            this.executionModel = executionModel;

            return this;

        }



        public Builder capabilities(List<RuntimeCapability> capabilities) {

            this.capabilities = capabilities == null ? null : List.copyOf(capabilities);

            return this;

        }



        public Builder addCapability(RuntimeCapability capability) {

            if (capability == null) {

                return this;

            }

            if (this.capabilities == null) {

                this.capabilities = new ArrayList<>();

            } else if (!(this.capabilities instanceof ArrayList)) {

                this.capabilities = new ArrayList<>(this.capabilities);

            }

            if (!this.capabilities.contains(capability)) {

                this.capabilities.add(capability);

            }

            return this;

        }



        public Builder debuggable(boolean debuggable) {

            if (debuggable) {

                addCapability(RuntimeCapability.DEBUG);

            }

            return this;

        }



        public Builder replayable(boolean replayable) {

            if (replayable) {

                addCapability(RuntimeCapability.REPLAY);

            }

            return this;

        }



        public Builder timeoutAware(boolean timeoutAware) {

            if (timeoutAware) {

                addCapability(RuntimeCapability.TIMEOUT);

            }

            return this;

        }



        public Builder defaultTimeout(String defaultTimeout) {

            this.defaultTimeout = defaultTimeout;

            return this;

        }



        public Builder delegation(RuntimeDelegationDefinition delegation) {

            this.delegation = delegation;

            return this;

        }



        public WorkflowRuntimeDefinition build() {

            if (capabilities != null && !(capabilities instanceof ArrayList)) {

                capabilities = new ArrayList<>(capabilities);

            }

            if (capabilities != null) {

                Set<RuntimeCapability> deduped = new LinkedHashSet<>(capabilities);

                capabilities = List.copyOf(deduped);

            }

            return new WorkflowRuntimeDefinition(this);

        }

    }

}


