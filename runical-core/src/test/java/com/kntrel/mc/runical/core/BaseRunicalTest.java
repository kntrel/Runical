package com.kntrel.mc.runical.core;

import com.kntrel.mc.runical.core.internal.LocaleSupport;
import com.kntrel.mc.runical.core.argument.Argument;
import com.kntrel.mc.runical.core.argument.BundledArgument;
import com.kntrel.mc.runical.core.argument.Translatable;
import com.kntrel.mc.runical.core.argument.TranslationProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertEquals("Exito general", runical.translate("es-mx", "command.success").message());

        ResolvedTranslation sibling = runical.translate("es-mx", "command.sibling").translation();
        assertEquals("Exito regional", sibling.value());
        assertEquals("es-ar", sibling.resolvedLocale());
        assertEquals(ResolutionSource.SIBLING, sibling.source());

        ResolvedTranslation fallback = runical.translate("fr-ca", "command.success").translation();
        assertEquals("Default success", fallback.value());
        assertEquals("en-us", fallback.resolvedLocale());
        assertEquals(ResolutionSource.DEFAULT, fallback.source());

        assertEquals("missing.key", runical.translate("fr-ca", "missing.key").message());
        assertNull(runical.translate("fr-ca", "missing.key").orNull().message());
        assertEquals(
                "Fallback value 12",
                runical.translate("fr-ca", "missing.key")
                        .argument("blockCount", 12)
                        .orDefault("Fallback value {blockCount}").message()
        );
    }

    @Test
    void formatsNamedPlaceholdersAndEscapedBraces() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello {player}, {{literal}} {missing} {amount}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        String translated = runical.translate("en-us", "greeting.message")
                .argument("player", "Alex")
                .argument("amount", 12)
                .message();

        assertEquals("Hello Alex, {literal} {missing} 12", translated);
    }

    @Test
    void supportsFluentTranslationArguments() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello {player}, you have {count} messages."
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        String translated = runical.translate("en-us", "greeting.message")
                .argument("player", "Alex")
                .argument("count", 12)
                .message();

        assertEquals("Hello Alex, you have 12 messages.", translated);
    }

    @Test
    void supportsBulkTranslationArguments() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello {player}, you have {count} messages."
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        String fromVarargs = runical.translate("en-us", "greeting.message")
                .arguments(
                        new Argument("player", "Alex"),
                        new Argument("count", 12)
                )
                .message();

        String fromCollection = runical.translate("en-us", "greeting.message")
                .arguments(List.of(
                        new Argument("player", "Sam"),
                        new Argument("count", 7)
                ))
                .message();

        assertEquals("Hello Alex, you have 12 messages.", fromVarargs);
        assertEquals("Hello Sam, you have 7 messages.", fromCollection);
    }

    @Test
    void asyncTerminalsSnapshotArgumentsWhenLookupStarts() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello {player}"
                """);

        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        RunicalOptions options = RunicalOptions.builder()
                .defaultLocale("en-us")
                .asyncExecutor(command -> assertTrue(queuedTask.compareAndSet(null, command)))
                .build();

        TestRunical runical = new TestRunical(this.tempDir, options);
        var job = runical.translate("en-us", "greeting.message").argument("player", "Alex");

        CompletableFuture<String> lookup = job.async().message();
        job.argument("player", "Sam");

        Runnable scheduledTask = queuedTask.get();
        assertNotNull(scheduledTask);
        scheduledTask.run();

        assertEquals("Hello Alex", lookup.join());
    }

    @Test
    void formatsBundledPlaceholdersIntoDottedTokens() throws Exception {
        write("en-us.yml", """
                region:
                  message: "Welcome to {region}. The name of the region {region.id} is {region.color}{region.name}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        BundledArgument region = BundledArgument.of("name", "Spawn")
                .append("id", 12)
                .append("color", "Blue ");

        String translated = runical.translate("en-us", "region.message")
                .argument("region", region)
                .message();

        assertEquals("Welcome to Spawn. The name of the region 12 is Blue Spawn", translated);
    }

    @Test
    void supportsNestedBundledPlaceholdersAndLaterOverrides() throws Exception {
        write("en-us.yml", """
                region:
                  message: "Owner {region.owner.name} lives in {region.name} ({region.id})"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        BundledArgument region = BundledArgument.of("name", "Spawn")
                .append("id", 12)
                .append("owner", BundledArgument.of("name", "Alex"));

        String translated = runical.translate("en-us", "region.message")
                .argument("region", region)
                .argument("region.owner.name", "Sam")
                .argument("region.id", 99)
                .message();

        assertEquals("Owner Sam lives in Spawn (99)", translated);
    }

    @Test
    void supportsOverridingBundledPlaceholderDefaultValue() throws Exception {
        write("en-us.yml", """
                region:
                  message: "Welcome to {region} ({region.id})"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        BundledArgument region = BundledArgument.of("name", "Spawn")
                .append("id", 12)
                .appendDefault("Region #12");

        String translated = runical.translate("en-us", "region.message")
                .argument("region", region)
                .message();

        assertEquals("Welcome to Region #12 (12)", translated);
    }

    @Test
    void projectsTranslatableObjectsFromFieldsAndGetters() throws Exception {
        write("en-us.yml", """
                person:
                  message: "{person} is {person.age} years old and has id {person.identifier}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals(
                "Alex is 29 years old and has id p-42",
                runical.translate("en-us", "person.message")
                        .argument("person", new AnnotatedPerson("Alex", 29, "p-42"))
                        .message()
        );
    }

    @Test
    void projectsNestedTranslatableObjectsRecursivelyAndSupportsRecordComponents() throws Exception {
        write("en-us.yml", """
                person:
                  message: "{person} lives in {person.address} {person.address.zip}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        AddressRecord address = new AddressRecord("San Jose", "10101");
        RecordBackedPerson person = new RecordBackedPerson("Alex", address);

        assertEquals(
                "Alex lives in San Jose 10101",
                runical.translate("en-us", "person.message").argument("person", person).message()
        );
    }

    @Test
    void doesNotProjectSubclassesUnlessTheyAreAnnotatedThemselves() throws Exception {
        write("en-us.yml", """
                person:
                  message: "{person} {person.name}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals(
                "Employee(Alex) {person.name}",
                runical.translate("en-us", "person.message")
                        .argument("person", new UnannotatedEmployee("Alex", 29))
                        .message()
        );
    }

    @Test
    void projectsAnnotatedSubclassesIncludingInheritedTranslationProperties() throws Exception {
        write("en-us.yml", """
                person:
                  message: "{person} is {person.age}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals(
                "Alex is 29",
                runical.translate("en-us", "person.message")
                        .argument("person", new AnnotatedEmployee("Alex", 29))
                        .message()
        );
    }

    @Test
    void rejectsMultipleRootTranslationProperties() throws Exception {
        write("en-us.yml", """
                person:
                  message: "{person}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertThrows(
                IllegalArgumentException.class,
                () -> runical.translate("en-us", "person.message")
                        .argument("person", new InvalidRootPerson("Alex", "Al"))
                        .message()
        );
    }

    @Test
    void validatesBundledPlaceholderSegmentNames() {
        assertThrows(IllegalArgumentException.class, () -> BundledArgument.of(" ", "value"));
        assertThrows(IllegalArgumentException.class, () -> BundledArgument.of("region.name", "value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BundledArgument.of("region", "value").append("owner.name", "Alex")
        );
    }

    @Test
    void resolvesTaggedFileTranslationsRelativeToTheLocaleFile() throws Exception {
        Path snippetsDirectory = Files.createDirectories(this.tempDir.resolve("snippets"));
        Files.writeString(snippetsDirectory.resolve("deeds.txt"), "First line%nHello {player}".formatted());
        write("en-us.yml", """
                totem:
                  region:
                    deeds: !file ./snippets/deeds.txt
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals(
                "First line%nHello Alex".formatted(),
                runical.translate("en-us", "totem.region.deeds").argument("player", "Alex").message()
        );
    }

    @Test
    void materializesMissingTaggedFilesThroughSubclassHook() throws Exception {
        write("en-us.yml", """
                totem:
                  region:
                    deeds: !file ./snippets/deeds.txt
                """);

        TaggedFileTestRunical runical = new TaggedFileTestRunical(
                this.tempDir,
                RunicalOptions.builder().defaultLocale("en-us").build(),
                Map.of("snippets/deeds.txt", "Bundled {player}")
        );

        assertEquals("Bundled Alex", runical.translate("en-us", "totem.region.deeds").argument("player", "Alex").message());
        assertTrue(Files.exists(this.tempDir.resolve("snippets").resolve("deeds.txt")));
        assertEquals(1, runical.fileLookupCount("snippets/deeds.txt"));
    }

    @Test
    void skipsMissingTaggedFileTranslationsWithoutDroppingTheWholeLocale() throws Exception {
        write("en-us.yml", """
                greeting:
                  message: "Hello"
                totem:
                  region:
                    deeds: !file ./snippets/missing.txt
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        assertEquals("Hello", runical.translate("en-us", "greeting.message").message());
        assertEquals("totem.region.deeds", runical.translate("en-us", "totem.region.deeds").message());
    }

    @Test
    void resolvesRelativeKeysThroughChildTranslators() throws Exception {
        write("en-us.yml", """
                totem:
                  region:
                    naming:
                      default: "Named {player}"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        BaseTranslator regionTranslator = runical.getChild("totem").getChild("region");

        assertEquals("totem", runical.getChild("totem").getPath());
        assertEquals("totem.region", regionTranslator.getPath());
        assertEquals(
                runical.translate("en-us", "totem.region.naming.default").argument("player", "Alex").message(),
                regionTranslator.translate("en-us", "naming.default").argument("player", "Alex").message()
        );

        ResolvedTranslation unresolved = regionTranslator.translate("en-us", "missing.key").translation();
        assertEquals("totem.region.missing.key", unresolved.key());
        assertEquals("totem.region.missing.key", unresolved.orKey());
    }

    @Test
    void cachesChildTranslatorsAndRejectsDottedSegments() throws Exception {
        write("en-us.yml", """
                totem:
                  region:
                    value: "ok"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());

        BaseTranslator totem = runical.getChild("totem");
        BaseTranslator sameTotem = runical.getChild("totem");
        BaseTranslator region = totem.getChild("region");
        BaseTranslator sameRegion = runical.getChild("totem").getChild("region");

        assertSame(totem, sameTotem);
        assertSame(region, sameRegion);
        assertThrows(IllegalArgumentException.class, () -> runical.getChild("totem.region"));
        assertThrows(IllegalArgumentException.class, () -> totem.getChild(" "));
    }

    @Test
    void mountsAliasesAtRootAndResolvesThroughChildTranslators() throws Exception {
        write("en-us.yml", """
                totem:
                  deeds:
                    prologue: "Deeds prologue"
                hierarchy:
                  1:
                    1:
                      name: "Admins"
                      description: "Everything"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        runical.mount("hierarchy", "totem.deeds.hierarchy");
        BaseTranslator deedsTranslator = runical.getChild("totem").getChild("deeds");
        BaseTranslator hierarchyAlias = deedsTranslator.getChild("hierarchy");

        assertEquals("totem.deeds", deedsTranslator.getPath());
        assertEquals("Deeds prologue", deedsTranslator.translate("en-us", "prologue").message());
        assertEquals("Admins", deedsTranslator.translate("en-us", "hierarchy.1.1.name").message());
        assertEquals("Everything", deedsTranslator.translate("en-us", "hierarchy.1.1.description").message());
        assertEquals("Admins", runical.translate("en-us", "totem.deeds.hierarchy.1.1.name").message());
        assertEquals("totem.deeds.hierarchy", hierarchyAlias.getPath());

        ResolvedTranslation localUnresolved = deedsTranslator.translate("en-us", "missing.key").translation();
        assertEquals("totem.deeds.missing.key", localUnresolved.key());

        ResolvedTranslation mountedUnresolved = deedsTranslator.translate("en-us", "hierarchy.missing.key").translation();
        assertEquals("totem.deeds.hierarchy.missing.key", mountedUnresolved.key());
    }

    @Test
    void mountsAliasesThroughTranslatorOverloads() throws Exception {
        write("en-us.yml", """
                totem:
                  deeds:
                    prologue: "Deeds prologue"
                hierarchy:
                  roles:
                    admin:
                      name: "Admins"
                """);

        TestRunical runical = new TestRunical(this.tempDir, RunicalOptions.builder().defaultLocale("en-us").build());
        BaseTranslator deeds = runical.getChild("totem").getChild("deeds");
        BaseTranslator hierarchy = runical.getChild("hierarchy");

        runical.mount(hierarchy, "totem.deeds.hierarchy");
        runical.mount(deeds, hierarchy, "admin_hierarchy");

        assertEquals("Admins", runical.translate("en-us", "totem.deeds.hierarchy.roles.admin.name").message());
        assertEquals("Admins", deeds.translate("en-us", "admin_hierarchy.roles.admin.name").message());
        assertEquals("totem.deeds.admin_hierarchy", deeds.getChild("admin_hierarchy").getPath());
        assertEquals("totem.deeds.hierarchy", deeds.getChild("hierarchy").getPath());
    }

    @Test
    void validatesRootMountRules() throws Exception {
        Path primaryDirectory = Files.createDirectories(this.tempDir.resolve("primary"));
        Path secondaryDirectory = Files.createDirectories(this.tempDir.resolve("secondary"));

        write(primaryDirectory, "en-us.yml", """
                totem:
                  deeds:
                    prologue: "Deeds"
                hierarchy:
                  roles:
                    admin:
                      name: "Admins"
                """);
        write(secondaryDirectory, "en-us.yml", """
                hierarchy:
                  roles:
                    admin:
                      name: "Other admins"
                """);

        TestRunical primaryRunical = new TestRunical(primaryDirectory, RunicalOptions.builder().defaultLocale("en-us").build());
        TestRunical secondaryRunical = new TestRunical(secondaryDirectory, RunicalOptions.builder().defaultLocale("en-us").build());

        assertThrows(IllegalArgumentException.class, () -> primaryRunical.mount("hierarchy", "hierarchy"));
        assertThrows(
                IllegalArgumentException.class,
                () -> primaryRunical
                        .mount("hierarchy", "totem.deeds.hierarchy")
                        .mount("hierarchy.roles", "totem.deeds.hierarchy.roles")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> primaryRunical.mount(primaryRunical.getChild("totem"), secondaryRunical.getChild("hierarchy"), "hierarchy")
        );
        assertThrows(IllegalArgumentException.class, () -> primaryRunical.mount(primaryRunical, "totem.root_alias"));
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

        runical.translate("es-mx", "value").message();
        assertTrue(runical.isLoaded("es-mx"));

        runical.translate("en-us", "value").message();
        runical.translate("en-us", "value").message();
        assertFalse(runical.isLoaded("es-mx"));

        runical.translate("es-mx", "value").message();
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
                runical.translate("en-us", "message").argument("player", "Alex").async().message(),
                runical.translate("en-us", "message").argument("player", "Sam").async().message(),
                runical.translate("en-us", "message").argument("player", "Morgan").async().message()
        );

        assertEquals(
                List.of("Hello Alex", "Hello Sam", "Hello Morgan"),
                futures.stream().map(CompletableFuture::join).toList()
        );

        assertEquals(
                "Fallback async 24",
                runical.translate("en-us", "missing.message")
                        .argument("blockCount", 24)
                        .orDefault("Fallback async {blockCount}").async().message().join()
        );
    }

    @Test
    void materializesMissingBundledFilesAcrossFallbackSteps() {
        BundledTestRunical runical = new BundledTestRunical(
                this.tempDir,
                RunicalOptions.builder().defaultLocale("en-us").build(),
                Map.of(
                        "es-mx", """
                                messages:
                                  exact: "Exact success"
                                """,
                        "es", """
                                messages:
                                  general: "General success"
                                """,
                        "es-ar", """
                                messages:
                                  sibling: "Sibling success"
                                """,
                        "en-us", """
                                messages:
                                  default: "Default success"
                                """
                )
        );

        ResolvedTranslation exact = runical.translate("es-mx", "messages.exact").translation();
        assertEquals("Exact success", exact.value());
        assertEquals(ResolutionSource.EXACT, exact.source());
        assertTrue(Files.exists(this.tempDir.resolve("es-mx.yml")));

        ResolvedTranslation general = runical.translate("es-mx", "messages.general").translation();
        assertEquals("General success", general.value());
        assertEquals("es", general.resolvedLocale());
        assertEquals(ResolutionSource.GENERAL, general.source());
        assertTrue(Files.exists(this.tempDir.resolve("es.yml")));

        ResolvedTranslation sibling = runical.translate("es-mx", "messages.sibling").translation();
        assertEquals("Sibling success", sibling.value());
        assertEquals("es-ar", sibling.resolvedLocale());
        assertEquals(ResolutionSource.SIBLING, sibling.source());
        assertTrue(Files.exists(this.tempDir.resolve("es-ar.yml")));

        ResolvedTranslation fallback = runical.translate("fr-ca", "messages.default").translation();
        assertEquals("Default success", fallback.value());
        assertEquals("en-us", fallback.resolvedLocale());
        assertEquals(ResolutionSource.DEFAULT, fallback.source());
        assertTrue(Files.exists(this.tempDir.resolve("en-us.yml")));

        assertEquals(1, runical.lookupCount("es-mx"));
        assertEquals(1, runical.lookupCount("es"));
        assertEquals(1, runical.lookupCount("es-ar"));
        assertEquals(1, runical.lookupCount("en-us"));
    }

    @Test
    void doesNotMaterializeBundledFileWhenExistingLocaleIsMissingOnlyTheKey() throws Exception {
        write("es.yml", """
                messages:
                  present: "Filesystem"
                """);

        BundledTestRunical runical = new BundledTestRunical(
                this.tempDir,
                RunicalOptions.builder().defaultLocale("es").build(),
                Map.of(
                        "es", """
                                messages:
                                  missing: "Bundled"
                                """
                )
        );

        assertEquals("messages.missing", runical.translate("es", "messages.missing").message());
        assertEquals(0, runical.lookupCount("es"));
    }

    private void write(String fileName, String content) throws IOException {
        write(this.tempDir, fileName, content);
    }

    private void write(Path directory, String fileName, String content) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fileName), content);
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

    private static final class TaggedFileTestRunical extends BaseRunical {
        private final Path languagesDirectory;
        private final Map<String, String> bundledFiles;
        private final ConcurrentHashMap<String, Integer> fileLookupCounts;

        private TaggedFileTestRunical(Path languagesDirectory, RunicalOptions options, Map<String, String> bundledFiles) {
            super(languagesDirectory, options);
            this.languagesDirectory = languagesDirectory;
            this.bundledFiles = new HashMap<>(bundledFiles);
            this.fileLookupCounts = new ConcurrentHashMap<>();
        }

        @Override
        protected Optional<Path> resolveMissingTaggedFile(Path localeFile, Path referencedPath) {
            Path relativePath = this.languagesDirectory.toAbsolutePath().normalize().relativize(referencedPath.toAbsolutePath().normalize());
            String key = relativePath.toString().replace('\\', '/');
            this.fileLookupCounts.merge(key, 1, Integer::sum);

            String content = this.bundledFiles.get(key);
            if (content == null) {
                return Optional.empty();
            }

            try {
                Path parent = referencedPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(referencedPath, content);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return Optional.of(referencedPath);
        }

        private int fileLookupCount(String relativePath) {
            return this.fileLookupCounts.getOrDefault(relativePath, 0);
        }
    }

    private static final class BundledTestRunical extends BaseRunical {
        private final Path languagesDirectory;
        private final Map<String, String> bundledLocales;
        private final ConcurrentHashMap<String, Integer> lookupCounts;

        private BundledTestRunical(Path languagesDirectory, RunicalOptions options, Map<String, String> bundledLocales) {
            super(languagesDirectory, options);
            this.languagesDirectory = languagesDirectory;
            this.bundledLocales = new HashMap<>();
            bundledLocales.forEach((locale, content) -> this.bundledLocales.put(LocaleSupport.normalizeLocale(locale), content));
            this.lookupCounts = new ConcurrentHashMap<>();
        }

        @Override
        protected Optional<Path> resolveMissingLocaleFile(String locale) {
            this.lookupCounts.merge(locale, 1, Integer::sum);

            String content = this.bundledLocales.get(locale);
            if (content == null) {
                return Optional.empty();
            }

            Path path = this.languagesDirectory.resolve(locale + ".yml");
            try {
                Files.writeString(path, content);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return Optional.of(path);
        }

        @Override
        protected List<String> additionalLocalesForLanguage(String language) {
            String normalizedLanguage = LocaleSupport.normalizeLocale(language);
            return this.bundledLocales.keySet().stream()
                    .filter(locale -> LocaleSupport.languageOf(locale).equals(normalizedLanguage))
                    .sorted()
                    .toList();
        }

        private int lookupCount(String locale) {
            return this.lookupCounts.getOrDefault(LocaleSupport.normalizeLocale(locale), 0);
        }
    }

    @Translatable
    private static class AnnotatedPerson {
        private final String name;

        @TranslationProperty
        private final int age;

        private final String identifier;

        private AnnotatedPerson(String name, int age, String identifier) {
            this.name = name;
            this.age = age;
            this.identifier = identifier;
        }

        @TranslationProperty(root = true)
        public String getName() {
            return this.name;
        }

        @TranslationProperty("identifier")
        public String getIdentifier() {
            return this.identifier;
        }
    }

    @Translatable
    private static final class RecordBackedPerson {
        private final String name;

        @TranslationProperty
        private final AddressRecord address;

        private RecordBackedPerson(String name, AddressRecord address) {
            this.name = name;
            this.address = address;
        }

        @TranslationProperty(root = true)
        public String getName() {
            return this.name;
        }
    }

    @Translatable
    private record AddressRecord(
            @TranslationProperty(root = true) String city,
            @TranslationProperty("zip") String postalCode
    ) {
    }

    @Translatable
    private static class BaseAnnotatedPerson {
        private final String name;

        private BaseAnnotatedPerson(String name) {
            this.name = name;
        }

        @TranslationProperty(root = true)
        public String getName() {
            return this.name;
        }
    }

    private static final class UnannotatedEmployee extends BaseAnnotatedPerson {
        @SuppressWarnings("unused")
        private final int age;

        private UnannotatedEmployee(String name, int age) {
            super(name);
            this.age = age;
        }

        @Override
        public String toString() {
            return "Employee(" + getName() + ")";
        }
    }

    @Translatable
    private static final class AnnotatedEmployee extends BaseAnnotatedPerson {
        @TranslationProperty
        private final int age;

        private AnnotatedEmployee(String name, int age) {
            super(name);
            this.age = age;
        }
    }

    @Translatable
    private static final class InvalidRootPerson {
        private final String primaryName;
        private final String secondaryName;

        private InvalidRootPerson(String primaryName, String secondaryName) {
            this.primaryName = primaryName;
            this.secondaryName = secondaryName;
        }

        @TranslationProperty(root = true)
        public String getPrimaryName() {
            return this.primaryName;
        }

        @TranslationProperty(root = true)
        public String getSecondaryName() {
            return this.secondaryName;
        }
    }
}
