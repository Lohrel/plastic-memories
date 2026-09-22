package dev.lohrel.plasticmemories.lorebook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lohrel.plasticmemories.memory.ConversationMemory;
import dev.lohrel.plasticmemories.npc.CookAvailability;
import dev.lohrel.plasticmemories.npc.NpcProfile;
import dev.lohrel.plasticmemories.provider.PromptBuilder;
import dev.lohrel.plasticmemories.provider.ProviderRequest;
import dev.lohrel.plasticmemories.provider.ProviderSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientLorebookPromptPipelineTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsAProviderOnlyPromptFromAnExplicitlyActivatedLocalImport() throws Exception {
        Path source = tempDir.resolve("forest.json");
        Files.writeString(source, """
                {"entries":[{"id":"forest","keys":["forest"],"content":"The forest is protected."}]}
                """);
        LorebookImportResult imported = LorebookImporter.importArtifact(source);
        ClientLorebookLibraryStore library = new ClientLorebookLibraryStore(tempDir.resolve("library"));
        UUID artifactId = library.store(imported);
        library.activateGlobal(artifactId);

        ImportedPromptContext context = new ClientLorebookRequestContextLoader(library).load(
                new LocalLorebookBindingKey("singleplayer:example", UUID.randomUUID(), UUID.randomUUID()),
                ConversationMemory.empty(),
                "Tell me about the forest.");
        ProviderRequest request = new PromptBuilder().build(
                ProviderSettings.create("https://example.com/v1", "test", "test-key"),
                "Warden",
                ConversationMemory.empty(),
                "Tell me about the forest.",
                CookAvailability.AVAILABLE,
                NpcProfile.empty(),
                context);

        String system = request.messages().getFirst().content();
        assertTrue(system.contains("The forest is protected."));
        assertTrue(system.indexOf("The forest is protected.") < system.indexOf("Reply naturally and concisely."));
    }
}
