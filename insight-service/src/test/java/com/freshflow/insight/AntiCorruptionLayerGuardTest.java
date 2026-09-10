package com.freshflow.insight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Anti-Corruption Layer, asserted mechanically over this service's own source tree.
 *
 * <p>spec-01 &sect;1.5 makes {@code pricing-service} an Anti-Corruption Layer against this one:
 * "Pricing independently validates the margin floor. LLM output is treated as an untrusted
 * proposal, never as an authoritative command." spec-01 &sect;2.5 says where the check lives, and
 * spec-02 FR14 says the agent "proposes only. It MUST NOT write prices directly." Team ruling R5
 * draws the consequence for this service: <b>no margin-floor logic anywhere in
 * {@code insight-service}</b> &mdash; not a constant, not a check, not a sentence in a prompt
 * template &mdash; and no call into Pricing, synchronous or otherwise.
 *
 * <p><b>Why a source scan rather than a design note.</b> This is the project's central architectural
 * argument, and an argument that rests on everyone remembering it is an argument that expires. Every
 * other way of breaching it fails loudly &mdash; a new dependency shows up in a {@code pom.xml}, a
 * new endpoint shows up in spec-04 &mdash; but adding a margin constant to a prompt string, or a
 * quiet REST call to port 8083, breaks nothing and no test. This one does.
 *
 * <p><b>Comments are stripped before the scan, and string literals are not.</b> Both halves matter.
 * Stripping comments is what lets the production code explain <i>why</i> a rule exists, and cite the
 * clause it comes from, without the explanation being mistaken for a breach; documenting an absence
 * is the opposite of a breach. Keeping string literals is what makes R5's "not a sentence in a
 * prompt template" enforceable at all, since a prompt is a string.
 */
@DisplayName("R5: insight-service enforces no margin floor and writes no price (spec-01 s1.5)")
class AntiCorruptionLayerGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /**
     * What must not appear in this service's code, lower-cased for comparison.
     *
     * <p>Three kinds of thing: a reference to the Pricing context's packages or service, the margin
     * floor in any spelling the codebase might use, and the Pricing endpoints that would write or
     * read a price. {@code getMarginFloor} is covered by the {@code marginfloor} entry.
     */
    private static final List<String> FORBIDDEN = List.of(
            "com.freshflow.pricing",
            "pricing-service",
            "marginfloor",
            "margin floor",
            "margin-floor",
            "margin_floor",
            "/api/prices",
            "/api/markdown-plans");

    @Test
    @DisplayName("no type in insight-service references Pricing, a margin floor, or a price write")
    void mainSources_containNoPricingReferenceAndNoMarginFloor() {
        List<Path> sources = mainSourceFiles();

        assertThat(sources)
                .as("the scan must actually have found this service's sources at %s; an empty "
                        + "list would make every assertion below vacuously true",
                        MAIN_SOURCES.toAbsolutePath())
                .isNotEmpty();

        List<String> breaches = new ArrayList<>();
        for (Path source : sources) {
            String code = stripComments(read(source)).toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN) {
                if (code.contains(forbidden)) {
                    breaches.add(source + " contains \"" + forbidden + "\" outside a comment");
                }
            }
        }

        assertThat(breaches)
                .as("spec-01 s1.5 and s2.5, spec-02 FR11 and FR14, team ruling R5: the margin floor "
                        + "is enforced in the Pricing context's domain layer and nowhere else, and "
                        + "the agent proposes only")
                .isEmpty();
    }

    // --------------------------------------------------------------------------------- helpers

    private static List<Path> mainSourceFiles() {
        if (!Files.isDirectory(MAIN_SOURCES)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Removes line and block comments, leaving string and character literals intact.
     *
     * <p>A small state machine rather than a regular expression, because a regular expression
     * cannot tell a {@code //} inside a string literal from the start of a comment, and getting
     * that backwards would make the guard either blind or hysterical.
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int length = source.length();

        while (i < length) {
            char c = source.charAt(i);
            char next = i + 1 < length ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                while (i < length && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < length && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, length);
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < length) {
                    char inside = source.charAt(i);
                    out.append(inside);
                    i++;
                    if (inside == '\\' && i < length) {
                        out.append(source.charAt(i));
                        i++;
                    } else if (inside == quote) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
