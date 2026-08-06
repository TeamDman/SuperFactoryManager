package ca.teamdman.sfm.client.text_editor;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable source descriptor resolved independently for each editor panel. */
public sealed interface SFMTextDocumentSource {
    String load();

    record Literal(String text) implements SFMTextDocumentSource {
        public Literal {
            Objects.requireNonNull(text);
        }

        @Override
        public String load() {
            return text;
        }
    }

    record ResourceAddress(ResourceLocation address) implements SFMTextDocumentSource {
        public ResourceAddress {
            Objects.requireNonNull(address);
        }

        @Override
        public String load() {
            Map<ResourceLocation, Resource> resources = Minecraft.getInstance().getResourceManager()
                    .listResources(address.getPath(), location -> location.equals(address));
            Resource resource = resources.get(address);
            if (resource == null) return "// Missing runtime resource: " + address;
            try (BufferedReader reader = resource.openAsReader()) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException exception) {
                return "// Failed to read runtime resource: " + address + "\n// " + exception.getMessage();
            }
        }
    }
}
