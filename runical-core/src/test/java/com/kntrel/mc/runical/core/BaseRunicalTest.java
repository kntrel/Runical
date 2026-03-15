package com.kntrel.mc.runical.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseRunicalTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesAcrossLocaleFallbacks() throws Exception {
        write("en-us.yml", """
                command:
                  success: "Default success"
                """);
        write("es.yml", """
                command:
                  success: "Exito general"
                """);
        write("es-ar.yml", """
                command:
                  sibling: "Exito regional"
                """);
        write("es-mx.yml", """
                command:
                  other: "Otro"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals("Exito general", runical.translate("es-mx", "command.success"));

        ResolvedTranslation sibling = runical.resolve("es-mx", "command.sibling");
        assertEquals("Exito regional", sibling.value());
        assertEquals("es-ar", sibling.resolvedLocale());
        assertEquals(ResolutionSource.SIBLING, sibling.source());

        ResolvedTranslation fallback = runical.resolve("fr-ca", "command.success");
        assertEquals("Default success", fallback.value());
        assertEquals("en-us", fallback.resolvedLocale());
        assertEquals(ResolutionSource.DEFAULT, fallback.source());

        assertEquals("missing.key", runical.translate("fr-ca", "missing.key"));
        assertNull(runical.translateOrNull("fr-ca", "missing.key"));
        assertEquals(
                "Fallback value 12",
                runical.translateOrDefault(
                        "fr-ca",
                        "missing.key",
                        "Fallback value {blockCount}",
                        Placeholder.of("blockCount", 12)
                )
        );
    }

    @Test
    void formatsNamedPlaceholdersAndEscapedBraces() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello {player}, {{literal}} {missing} {amount}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        String translated = runical.translate(
                "en-us",
                "greeting.message",
                Placeholder.of("player", "Alex"),
                Placeholder.of("amount", 12)
        );

        assertEquals("Hello Alex, {literal} {missing} 12", translated);
    }

    @Test
    void formatsListsFromLocaleMetadataAndBuiltInFallback() throws Exception {
        write("en-us.yml", """
                _runical:
                  list_formats:
                    and:
                      two: "{0} & {1}"
                """);
        write("es.yml", """
                _runical:
                  list_formats:
                    and:
                      two: "{0} y {1}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals("A y B", runical.formatList("es-mx", List.of("A", "B")));
        assertEquals("A, B, or C", runical.formatList("fr-ca", List.of("A", "B", "C"), ListStyle.OR));
    }

    @Test
    void evictsIdleAndReleasedLocales() throws Exception {
        write("en-us.yml", """
                value: "default"
                """);
        write("es-mx.yml", """
                value: "regional"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder()
                .defaultLocale("en-us")
                .idleQueryThreshold(2)
                .cleanupIntervalQueries(1)
                .maxLoadedLocales(10)
                .build());

        runical.translate("es-mx", "value");
        assertTrue(runical.isLoaded("es-mx"));

        runical.translate("en-us", "value");
        runical.translate("en-us", "value");
        assertFalse(runical.isLoaded("es-mx"));

        runical.translate("es-mx", "value");
        runical.retain("es-mx");
        assertTrue(runical.isLoaded("es-mx"));
        runical.release("es-mx");
        assertFalse(runical.isLoaded("es-mx"));
    }

    @Test
    void resolvesAsyncConcurrently() throws Exception {
        write("en-us.yml", """
                message: "Hello {player}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        List<CompletableFuture<String>> futures = List.of(
                runical.translateAsync("en-us", "message", Placeholder.of("player", "Alex")),
                runical.translateAsync("en-us", "message", Placeholder.of("player", "Sam")),
                runical.translateAsync("en-us", "message", Placeholder.of("player", "Morgan"))
        );

        assertEquals(
                List.of("Hello Alex", "Hello Sam", "Hello Morgan"),
                futures.stream().map(CompletableFuture::join).toList()
        );

        assertEquals(
                "Fallback async 24",
                runical.translateOrDefaultAsync(
                        "en-us",
                        "missing.message",
                        "Fallback async {blockCount}",
                        Placeholder.of("blockCount", 24)
                ).join()
        );
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(this.tempDir.resolve(fileName), content);
    }

    private static final class TestRunical extends BaseRunical {
        private TestRunical(Path languagesDirectory, RunicalOptions options) {
            super(languagesDirectory, options);
        }

        private void retain(String locale) {
            retainLocale(locale);
        }

        private void release(String locale) {
            releaseLocale(locale);
        }
    }
}
