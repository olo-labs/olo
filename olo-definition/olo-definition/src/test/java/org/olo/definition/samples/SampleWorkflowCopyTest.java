package org.olo.definition.samples;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.serializer.YamlWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates canonical samples and {@code -copy} sidecars via {@link WorkflowDefinition#copy()},
 * then verifies on-disk files are byte-identical.
 */
class SampleWorkflowCopyTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();
    private final YamlWorkflowSerializer yaml = new YamlWorkflowSerializer();

    @Test
    void copySerializesIdenticalSampleFilesSideBySide() throws IOException {
        Path samplesRoot = SamplePaths.resolveSamplesRoot();
        SampleWorkflowGenerator generator = new SampleWorkflowGenerator();
        generator.generateCopies(samplesRoot);

        for (SampleWorkflowGenerator.SampleEntry entry : SampleWorkflowGenerator.SAMPLE_ENTRIES) {
            Path dir = samplesRoot.resolve(entry.folder());
            assertCopySidecarMatchesOriginal(dir, entry.baseName(), "json");
            assertCopySidecarMatchesOriginal(dir, entry.baseName(), "yaml");
        }
    }

    private void assertCopySidecarMatchesOriginal(Path dir, String baseName, String extension) throws IOException {
        Path original = dir.resolve(baseName + "." + extension);
        Path copy = dir.resolve(SampleWorkflowGenerator.copyBaseName(baseName) + "." + extension);

        assertThat(Files.exists(original)).as("expected sample file %s", original).isTrue();
        assertThat(Files.exists(copy)).as("expected copy file %s", copy).isTrue();

        WorkflowDefinition originalWorkflow = deserialize(dir, baseName, extension);
        WorkflowDefinition copiedWorkflow = deserialize(dir, SampleWorkflowGenerator.copyBaseName(baseName), extension);
        assertThat(copiedWorkflow)
                .as("copy sidecar must match original for %s", original.getFileName())
                .isEqualTo(originalWorkflow);
    }

    private WorkflowDefinition deserialize(Path dir, String baseName, String extension) throws IOException {
        Path file = dir.resolve(baseName + "." + extension);
        String content = Files.readString(file);
        return "yaml".equals(extension) ? yaml.deserialize(content) : json.deserialize(content);
    }

}
