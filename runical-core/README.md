# Runical Core

`runical-core` is the platform-agnostic translation engine behind Runical. It resolves translations
from YAML locale files, renders named placeholders, formats locale-aware lists, and supports both
synchronous and asynchronous lookups.

## What the core API provides

- Directory-backed locale loading from `.yml` and `.yaml` files
- Locale fallback from exact locale to general language, sibling regional locales, and a default locale
- Named placeholder rendering with brace escaping
- Path-scoped child translators through `getChild(...)`
- Root-level alias mounts through `BaseRunical.mount(...)`
- Locale-aware list formatting for `and` and `or` styles
- Lazy loading with cache eviction controls
- Protected retention hooks for platform adapters that track active user locales

## Quick start

`BaseRunical` is abstract so integrations can expose their own constructor or add platform-specific
behavior. It also implements `BaseTranslator`, so the root resolver and child translators share the
same key-lookup contract. A minimal wrapper looks like this:

```java
import com.kntrel.mc.runical.core.BaseRunical;
import com.kntrel.mc.runical.core.placeholder.Placeholder;
import com.kntrel.mc.runical.core.ResolvedTranslation;
import com.kntrel.mc.runical.core.RunicalOptions;

import java.nio.file.Path;

public final class RunicalCore extends BaseRunical {
    public RunicalCore(Path languagesDirectory, RunicalOptions options) {
        super(languagesDirectory, options);
    }
}
```

Example usage:

```java
try (RunicalCore runical = new RunicalCore(
        Path.of("plugins/example/lang"),
        RunicalOptions.builder()
                .defaultLocale("en-us")
                .build()
)) {
    String message = runical.translate(
            "es-mx",
            "greeting.message",
            Placeholder.of("player", "Alex")
    ).message();

    BaseTranslator regionTranslator = runical.getChild("totem").getChild("region");
    String regionName = regionTranslator.translate("es-mx", "naming.default").message();

    ResolvedTranslation resolved = runical.translate("fr-ca", "greeting.message").translation();
    String list = runical.formatList("es-mx", java.util.List.of("A", "B", "C"));
}
```

## Locale directory and file naming

Runical scans the configured language directory when the resolver is created and again whenever
`reload()` is called.

- Only top-level files are scanned.
- Supported file extensions are `.yml` and `.yaml`.
- The file name without the extension becomes the locale identifier.
- Locale names are normalized by trimming whitespace, replacing `_` with `-`, and converting to lowercase.

Examples:

- `en-us.yml` -> `en-us`
- `en_US.yml` -> `en-us`
- `es.yml` -> `es`

If two files normalize to the same locale, the first one discovered wins and the duplicate is
ignored with a warning.

If the directory does not exist yet, Runical creates it during scanning.

## Translation file format

Locale files are regular YAML maps. Nested objects are flattened into dot-separated translation
keys. Scalar values become translations. Lists and `null` values are skipped.

Example:

```yaml
greeting:
  message: "Hello {player}"
admin:
  reload:
    success: "Reloaded {count} locales"
```

This produces the keys:

- `greeting.message`
- `admin.reload.success`

Non-string scalar values are converted to strings when loaded, so values like numbers and booleans
are valid translation values.

Tagged `!file` scalar values load their translation text from the filesystem instead of using the
path literal itself. Relative paths are resolved from the locale YAML file's directory.

Example:

```yaml
totem:
  region:
    deeds: !file ./snippets/deeds.txt
```

If `./snippets/deeds.txt` contains `Hello {player}`, then `totem.region.deeds` resolves to that
file content and still participates in normal placeholder rendering.

The `_runical` root key is reserved for metadata. It is not flattened into translation keys.

## Placeholder rendering

Runical uses named placeholders inside braces:

```yaml
greeting:
  message: "Hello {player}, you have {count} messages."
```

```java
String translated = runical.translate(
        "en-us",
        "greeting.message",
        Placeholder.of("player", "Alex"),
        Placeholder.of("count", 12)
);
```

Rendering rules:

- Placeholder names are matched exactly.
- Placeholder values are converted with `String.valueOf(...)`.
- `BundledPlaceholder` values expose the bundle default through the root token such as `{region}`.
- `BundledPlaceholder` values also expand into dotted placeholder tokens such as `{region.name}`.
- If a placeholder is missing, the token stays in the output unchanged.
- `null` entries in the placeholder argument array are ignored.
- Use `{{` for a literal `{` and `}}` for a literal `}`.

Example:

```yaml
example:
  message: "Hello {player}, {{literal}} {missing}"
```

With `Placeholder.of("player", "Alex")`, the result is:

```text
Hello Alex, {literal} {missing}
```

Bundled placeholders let you group related values under one namespace:

```java
BundledPlaceholder region = BundledPlaceholder.of("name", "Spawn")
        .append("id", 12)
        .append("color", "Blue ");

String translated = runical.translate(
        "en-us",
        "region.message",
        Placeholder.of("region", region)
);
```

```yaml
region:
  message: "The name of the region {region.id} is {region.color}{region.name}"
```

The first value added with `of(...)` becomes the namespace default, so `{region}` would render as
`Spawn` in the example above. You can override that root value explicitly:

```java
region.appendDefault("Region #12");
```

## Locale fallback order

Translation lookup follows this order:

1. Exact locale, such as `es-mx`
2. General language locale, such as `es`
3. Sibling regional locales for the same language, such as `es-ar`
4. Configured default locale, such as `en-us`
5. Unresolved result

Behavior by API:

- `translate(...).message()` returns the translation key when unresolved.
- `translate(...).orNull().message()` returns `null` when unresolved.
- `translate(...).translation()` returns a `ResolvedTranslation` with `source == ResolutionSource.UNRESOLVED`.

`ResolvedTranslation` also tells you which locale actually provided the translation through
`resolvedLocale()` and which fallback branch succeeded through `source()`.

## Translator tree

Runical translators can be scoped to a fixed key path. The root `BaseRunical` instance acts as the
root translator, and each call to `getChild(...)` adds one path segment.

```java
BaseTranslator regionTranslator = runical.getChild("totem").getChild("region");

String rootValue = runical.translate("en-us", "totem.region.naming.default");
String childValue = regionTranslator.translate("en-us", "naming.default");
```

Those two lookups are equivalent. Child translators do not have their own cache or fallback logic;
they only prepend their path and delegate back to the root resolver.

## Root alias mounts

Sometimes a domain object wants one translator contract even though some of its keys live in
another subtree. Instead of creating virtual composite translators, Runical can register root-level
alias paths that behave like symbolic links:

```java
runical.mount("hierarchy", "totem.deeds.hierarchy");
```

After that registration:

- `runical.translate("en-us", "totem.deeds.hierarchy.1.1.name")` resolves through `hierarchy.1.1.name`
- `runical.getChild("totem").getChild("deeds").getChild("hierarchy").getPath()` returns `totem.deeds.hierarchy`
- querying that path from the root works because the alias is registered on the root itself

There are also translator-based overloads:

```java
runical.mount(runical.getChild("hierarchy"), "totem.deeds.hierarchy");

runical.mount(
        runical.getChild("totem").getChild("deeds"),
        runical.getChild("hierarchy"),
        "hierarchy"
);
```

Alias semantics:

- alias paths are real query paths on the root, not just local translator views
- child translators created from alias paths stay honest: `getPath()` returns the alias path and that path works from the root
- `ResolvedTranslation.key()` and `orKey()` stay in the originally queried keyspace rather than switching to the mounted target path
- an exact alias prefix owns that subtree, so real translations under the same alias path are intentionally shadowed

Alias guardrails:

- mounted translators must share the same underlying `BaseRunical` root
- mounted translators used in translator overloads must expose a non-root path
- overlapping alias paths such as `hierarchy` and `hierarchy.roles` are rejected

## List formatting

Runical can format a collection into a localized natural-language list:

```java
String andList = runical.formatList("en-us", java.util.List.of("A", "B", "C"));
String orList = runical.formatList("en-us", java.util.List.of("A", "B", "C"), ListStyle.OR);
```

By default:

- `formatList(locale, items)` uses `ListStyle.AND`
- `formatList(locale, items, ListStyle.OR)` uses `or` patterns

List patterns live under `_runical.list_formats`:

```yaml
_runical:
  list_formats:
    and:
      empty: ""
      two: "{0} and {1}"
      start: "{0}, {1}"
      middle: "{0}, {1}"
      end: "{0}, and {1}"
    or:
      two: "{0} or {1}"
      end: "{0}, or {1}"
```

Supported pattern keys:

- `empty`: output for an empty collection
- `two`: output for exactly two items
- `start`: first reduction step for three or more items
- `middle`: middle reduction steps
- `end`: final reduction step

Inside list patterns:

- `{0}` is the left side of the reduction
- `{1}` is the right side of the reduction

Missing list pattern keys fall back to the built-in defaults for the selected `ListStyle`.

List-format lookup follows the same locale fallback order as translation lookup. If no locale
provides list metadata, the built-in English defaults are used.

## Public API guide

The main user-facing methods on `BaseRunical` are:

- `translate(...)`: start a translation job
- `translate(...).message()`: resolve a translation and fall back to the key
- `translate(...).orNull().message()`: resolve a translation and fall back to `null`
- `translate(...).orDefault(...).message()`: resolve a translation and fall back to a rendered default
- `translate(...).translation()`: resolve a translation and keep the lookup metadata
- `translate(...).async().message()`: async message lookup
- `translate(...).async().translation()`: async raw lookup
- `getChild(...)`: create a child translator rooted at a key prefix
- `formatList(...)`: format a collection using localized list rules
- `hasLocale(...)`: check whether a locale file exists in the current index
- `isLoaded(...)`: check whether a locale is currently cached
- `preload(...)`: load a locale ahead of time
- `reload()`: rescan the language directory and clear the cache
- `evict(...)` and `evictAll()`: remove cached locales manually
- `getDefaultLocale()` and `setDefaultLocale(...)`: inspect or change the fallback locale
- `close()`: clear internal state and optionally shut down the async executor

## Configuration with RunicalOptions

`RunicalOptions` configures fallback, caching, and async behavior.

Default values:

- `defaultLocale("en-us")`
- `idleQueryThreshold(500)`
- `cleanupIntervalQueries(50)`
- `maxLoadedLocales(32)`
- `asyncExecutor(null)` which means Runical creates a virtual-thread-per-task executor
- `shutdownAsyncExecutorOnClose(false)` unless you explicitly set it

Notes:

- `idleQueryThreshold(0)` disables idle eviction.
- `maxLoadedLocales(0)` disables the cache size limit.
- `cleanupIntervalQueries(...)` must be greater than zero.
- When Runical creates its own async executor, it always shuts that executor down during `close()`.
- If you provide your own executor, set `shutdownAsyncExecutorOnClose(true)` only when Runical
  should own that executor's lifecycle.

Example:

```java
RunicalOptions options = RunicalOptions.builder()
        .defaultLocale("en-us")
        .idleQueryThreshold(250)
        .cleanupIntervalQueries(25)
        .maxLoadedLocales(16)
        .asyncExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .shutdownAsyncExecutorOnClose(true)
        .build();
```

## Cache behavior and retention hooks

Locale bundles are loaded lazily on first use and cached in memory.

Cleanup can evict locales for two reasons:

- The locale has been idle for at least `idleQueryThreshold` queries.
- The number of loaded locales exceeds `maxLoadedLocales`, in which case the least recently used
  non-retained locale is evicted.

Subclasses can protect actively used locales from eviction with these protected hooks:

- `retainLocale(locale)`
- `releaseLocale(locale)`
- `replaceRetainedLocale(oldLocale, newLocale)`

These hooks are useful for adapters that keep a locale attached to a live player, session, or
request context. `releaseLocale(...)` immediately evicts the locale when its retention count drops
to zero.

## Threading and lifecycle

`BaseRunical` is designed for concurrent use.

- Synchronous methods perform the lookup on the caller thread.
- Async methods delegate to the configured executor.
- `close()` is idempotent.
- After `close()`, all public methods except `close()` throw `IllegalStateException`.
- `reload()` clears the loaded-locale cache and rescans the directory, but it does not change the
  current default locale.

## Testing your locale data

The tests in `runical-core` are a good reference for supported behavior:

- fallback across exact, general, sibling, and default locales
- placeholder rendering and escaped braces
- localized list formatting
- idle and retained locale cache behavior
- concurrent async resolution
