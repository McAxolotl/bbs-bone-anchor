package mcaxolotl.boneanchor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import com.google.gson.*;

/**
 * The invariants every review of this addon used to re-check by hand: mixin registration, the
 * localization set, metadata, leftover conventions, smoke wiring and the documentation claims.
 * Runs without a game, so {@code ./gradlew check} enforces all of them from now on.
 */
public final class ProjectAuditTest {
    private static final List<String> LANGUAGES = List.of(
        "ar_ar", "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "id_id", "ko_kr", "pl_pl",
        "pt_br", "pt_pt", "ru_ru", "th_th", "tr_tr", "uk_ua", "ur_pk", "vi_vn", "zh_cn", "zh_tw");
    private static final String MENU_ACTION = "bbs_bone_anchor.bound_only";
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME|XXX|HACK)\\b");
    private static final Pattern RUN = Pattern.compile("(?m)^\\s*public static (int|String) run\\(\\)");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static int count;
    private static Path root;

    private static void check(boolean condition, String message) { count++; if (!condition) throw new AssertionError(message); }
    private static String read(Path path) throws IOException { return Files.readString(path, java.nio.charset.StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        root = Path.of("");
        check(Files.isRegularFile(root.resolve("build.gradle")), "Run the audit from the project directory");
        auditMixins();
        auditLocalizations();
        auditMetadata();
        auditSources();
        auditSmokeWiring();
        auditDocumentation();
        System.out.println("Project audit: " + count + " checks passed");
    }

    /** A mixin that is registered but missing, or written but never registered, silently does nothing. */
    private static void auditMixins() throws IOException {
        JsonObject mixins = JsonParser.parseString(read(root.resolve("src/main/resources/bbs_bone_anchor.mixins.json"))).getAsJsonObject();
        Set<String> listed = new TreeSet<>();
        for (JsonElement element : mixins.getAsJsonArray("client")) listed.add(element.getAsString());
        Set<String> written = new TreeSet<>();
        try (var stream = Files.list(root.resolve("src/main/java/mcaxolotl/boneanchor/compat/mixin"))) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java")) written.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        Set<String> difference = new TreeSet<>(listed);
        difference.addAll(written);
        difference.removeIf(name -> listed.contains(name) && written.contains(name));
        check(listed.equals(written), "Mixins are registered exactly once each: " + difference);
    }

    /** One file per language BBS ships, each carrying exactly the action the addon adds. */
    private static void auditLocalizations() throws IOException {
        Path directory = root.resolve("src/main/resources/assets/bbs_bone_anchor/assets/strings");
        List<String> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path file : stream.toList()) files.add(file.getFileName().toString());
        }
        Collections.sort(files);
        List<String> expected = new ArrayList<>();
        for (String language : LANGUAGES) expected.add(language + ".json");
        check(files.equals(expected), "Language files match BBS's set: " + files);

        for (String language : LANGUAGES) {
            Path file = directory.resolve(language + ".json");
            JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();
            check(json.size() == 1 && json.has(MENU_ACTION), "Only the addon's own string is defined: " + language);
            check(!json.get(MENU_ACTION).getAsString().isBlank(), "Translation is not blank: " + language);
        }
    }

    /** Fabric metadata has to point at things that exist, or the addon loads half-wired. */
    private static void auditMetadata() throws IOException {
        JsonObject mod = JsonParser.parseString(read(root.resolve("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        check("bbs_bone_anchor".equals(mod.get("id").getAsString()), "Mod id is stable");
        check("${version}".equals(mod.get("version").getAsString()), "Version comes from the build");
        check("client".equals(mod.get("environment").getAsString()), "The addon stays client-side");
        check(Files.isRegularFile(root.resolve("src/main/resources/" + mod.get("icon").getAsString())), "Metadata icon exists");

        for (var entrypoint : mod.getAsJsonObject("entrypoints").entrySet()) {
            for (JsonElement element : entrypoint.getValue().getAsJsonArray()) {
                String className = element.getAsString();
                check(Files.isRegularFile(root.resolve("src/main/java/" + className.replace('.', '/') + ".java")),
                    "Entrypoint source exists: " + entrypoint.getKey() + " -> " + className);
            }
        }
        for (String dependency : new String[]{"fabricloader", "minecraft", "java", "fabric-api", "bbs"}) {
            check(mod.getAsJsonObject("depends").has(dependency), "Declared dependency: " + dependency);
        }
    }

    /** Conventions this codebase keeps: English sources, no TODO markers, no unused imports. */
    private static void auditSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String tree : new String[]{"src/main/java", "src/test/java"}) {
            try (var stream = Files.walk(root.resolve(tree))) {
                for (Path file : stream.toList()) if (file.toString().endsWith(".java")) sources.add(file);
            }
        }
        List<String> unused = new ArrayList<>();
        for (Path file : sources) {
            String text = read(file);
            if (file.startsWith(root.resolve("src/main/java"))) {
                check(!CJK.matcher(text).find(), "Main sources stay in English: " + root.relativize(file));
                check(!TODO.matcher(text).find(), "No leftover markers: " + root.relativize(file));
            }
            StringBuilder body = new StringBuilder();
            for (String line : text.split("\n", -1)) if (!line.startsWith("import ")) body.append(line).append('\n');
            for (String line : text.split("\n", -1)) {
                if (!line.startsWith("import ") || line.endsWith(".*;")) continue;
                String type = line.substring(line.lastIndexOf('.') + 1, line.length() - 1);
                if (!Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(body).find())
                    unused.add(root.relativize(file) + ": " + type);
            }
        }
        check(unused.isEmpty(), "No unused imports: " + unused);
    }

    /** A suite that nobody runs is a suite that rots; the in-game report must name each one. */
    private static void auditSmokeWiring() throws IOException {
        Path directory = root.resolve("src/test/java/mcaxolotl/boneanchor");
        String integration = read(directory.resolve("IntegrationTestAddon.java"));
        List<String> suites = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path file : stream.toList()) {
                if (!file.toString().endsWith(".java")) continue;
                String text = read(file);
                if (RUN.matcher(text).find()) suites.add(file.getFileName().toString());
            }
        }
        check(suites.size() >= 5, "Expected the regression suites to be discovered: " + suites);
        for (String suite : suites) {
            String type = suite.substring(0, suite.length() - ".java".length());
            if (type.equals("IntegrationTestAddon")) continue;
            check(integration.contains(type + ".run()"), "In-game report covers " + type);
        }
    }

    /** The readme is the only changelog; a release that forgets to update it is a review failure. */
    private static void auditDocumentation() throws IOException {
        Matcher version = Pattern.compile("version = '([^']+)'").matcher(read(root.resolve("build.gradle")));
        check(version.find(), "Build declares a version");
        String declared = version.group(1);
        for (String readme : new String[]{"README.md", "README.zh-CN.md"}) {
            /* The documentation lands in its own commit: an earlier tree simply has none to audit. */
            if (!Files.isRegularFile(root.resolve(readme))) continue;
            String text = read(root.resolve(readme));
            check(text.contains("v" + declared), readme + " carries version " + declared);
            check(text.contains(String.valueOf(LANGUAGES.size())), readme + " states the supported language count");
        }
    }

    private ProjectAuditTest() {}
}
