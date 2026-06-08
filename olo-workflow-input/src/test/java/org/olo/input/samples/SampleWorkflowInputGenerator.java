package org.olo.input.samples;

import org.olo.input.model.WorkflowInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Writes canonical workflow invocation samples (JSON) under {@code samples/}.
 * Invoked by the {@code generateSamples} Gradle task and sample E2E tests.
 */
public final class SampleWorkflowInputGenerator {

    private static final String BASE_NAME = "workflow-input";

    static final List<SampleEntry> SAMPLE_ENTRIES = List.of(
            new SampleEntry("minimal-local", SampleWorkflowInputDefinitions::minimalLocal),
            new SampleEntry("mixed-storage", SampleWorkflowInputDefinitions::mixedStorage),
            new SampleEntry("producer-offload", SampleWorkflowInputDefinitions::producerOffload),
            new SampleEntry("cache-in-memory", SampleWorkflowInputDefinitions::cacheInMemory),
            new SampleEntry("typed-inputs", SampleWorkflowInputDefinitions::typedInputs),
            new SampleEntry("agent-execution", SampleWorkflowInputDefinitions::agentExecution),
            new SampleEntry("workflow-run", SampleWorkflowInputDefinitions::workflowRun),
            new SampleEntry("storage-remote", SampleWorkflowInputDefinitions::storageRemote),
            new SampleEntry("rag-metadata", SampleWorkflowInputDefinitions::ragMetadata));

    public static void main(String[] args) throws IOException {
        Path samplesRoot = args.length > 0 ? Path.of(args[0]) : Path.of("samples");
        new SampleWorkflowInputGenerator().generate(samplesRoot);
    }

    public void generate(Path samplesRoot) throws IOException {
        for (SampleEntry entry : SAMPLE_ENTRIES) {
            writeSample(samplesRoot.resolve(entry.folder()), entry.factory());
        }
    }

    /**
     * Writes {@code workflow-input-copy.json} next to each canonical sample file.
     */
    public void generateCopies(Path samplesRoot) throws IOException {
        for (SampleEntry entry : SAMPLE_ENTRIES) {
            writeCopySidecar(samplesRoot.resolve(entry.folder()), entry.factory());
        }
    }

    private void writeSample(Path dir, Supplier<WorkflowInput> factory) throws IOException {
        WorkflowInput input = factory.get();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(BASE_NAME + ".json"), input.toJson());
    }

    private void writeCopySidecar(Path dir, Supplier<WorkflowInput> factory) throws IOException {
        WorkflowInput copy = factory.get().copy();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(copyBaseName() + ".json"), copy.toJson());
    }

    static String copyBaseName() {
        return BASE_NAME + "-copy";
    }

    record SampleEntry(String folder, Supplier<WorkflowInput> factory) {
    }
}
