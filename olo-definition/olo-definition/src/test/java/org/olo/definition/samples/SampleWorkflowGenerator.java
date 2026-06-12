package org.olo.definition.samples;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.serializer.YamlWorkflowSerializer;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Writes canonical workflow samples (JSON and YAML) under {@code samples/}.
 * Invoked by {@link SampleWorkflowRegenerationTest} ({@code generateSamples} Gradle task) and sample E2E tests.
 */
public final class SampleWorkflowGenerator {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();
    private final YamlWorkflowSerializer yaml = new YamlWorkflowSerializer();

    static final List<SampleEntry> SAMPLE_ENTRIES = List.of(
            new SampleEntry("minimal-echo", "workflow", SampleWorkflowDefinitions::minimalEcho),
            new SampleEntry("stock-analysis", "workflow", SampleWorkflowDefinitions::stockAnalysis),
            new SampleEntry("rag-chat", "workflow", SampleWorkflowDefinitions::ragChat),
            new SampleEntry("condition-branch", "workflow", SampleWorkflowDefinitions::conditionBranch),
            new SampleEntry("human-approval-trade", "workflow", SampleWorkflowDefinitions::humanApprovalTrade),
            new SampleEntry(
                    "multi-agent-orchestration", "workflow", SampleWorkflowDefinitions::multiAgentOrchestration),
            new SampleEntry("parallel-agent-fan-out", "workflow", SampleWorkflowDefinitions::parallelAgentFanOut),
            new SampleEntry("research-agent", "workflow", SampleWorkflowDefinitions::researchAgent),
            new SampleEntry(
                    "technical-analysis-agent", "workflow", SampleWorkflowDefinitions::technicalAnalysisAgent),
            new SampleEntry(
                    "analysis-with-rag-extension", "workflow-base", SampleWorkflowDefinitions::analysisBase),
            new SampleEntry(
                    "analysis-with-rag-extension", "workflow-extended", SampleWorkflowDefinitions::analysisExtended));

    public static void main(String[] args) throws IOException {
        Path samplesRoot = args.length > 0 ? Path.of(args[0]) : Path.of("samples");
        new SampleWorkflowGenerator().generate(samplesRoot);
    }

    public void generate(Path samplesRoot) throws IOException {
        for (SampleEntry entry : SAMPLE_ENTRIES) {
            writeSample(samplesRoot.resolve(entry.folder()), entry.baseName(), entry.factory());
        }
    }

    /**
     * Writes {@code <base>-copy.json} and {@code <base>-copy.yaml} next to each canonical sample file.
     * Content is produced from {@link WorkflowDefinition#copy()} of the in-code sample.
     */
    public void generateCopies(Path samplesRoot) throws IOException {
        for (SampleEntry entry : SAMPLE_ENTRIES) {
            writeCopySidecar(samplesRoot.resolve(entry.folder()), entry.baseName(), entry.factory());
        }
    }

    private void writeSample(Path dir, String baseName, Supplier<WorkflowDefinition> factory) throws IOException {
        WorkflowDefinition workflow = factory.get();
        WorkflowValidator.validateOrThrow(workflow);
        Files.createDirectories(dir);

        Path jsonFile = dir.resolve(baseName + ".json");
        Path yamlFile = dir.resolve(baseName + ".yaml");
        Files.writeString(jsonFile, json.serialize(workflow));
        Files.writeString(yamlFile, yaml.serialize(workflow));
    }

    private void writeCopySidecar(Path dir, String baseName, Supplier<WorkflowDefinition> factory) throws IOException {
        WorkflowDefinition copy = factory.get().copy();
        WorkflowValidator.validateOrThrow(copy);
        Files.createDirectories(dir);

        Files.writeString(dir.resolve(copyBaseName(baseName) + ".json"), json.serialize(copy));
        Files.writeString(dir.resolve(copyBaseName(baseName) + ".yaml"), yaml.serialize(copy));
    }

    static String copyBaseName(String baseName) {
        return baseName + "-copy";
    }

    record SampleEntry(String folder, String baseName, Supplier<WorkflowDefinition> factory) {
    }
}
