package com.freshflow.insight.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec-01 &sect;2.2's dependency rule, asserted mechanically over this service's {@code domain}
 * package (ADR 0006).
 *
 * <p>The rule is categorical: the {@code domain} package "MUST NOT import from {@code presentation},
 * {@code application}, {@code infrastructure}, Spring, JPA, Jackson, or Kafka". ADR 0006 applies it
 * to the package it creates for {@code insight-service} and adds LangChain4j to the list, for the
 * reason ADR 0004 gives: a rule that would be enforced by a model library's absence is a rule that
 * stops being enforced the moment the library is on the classpath. This test states the positive
 * form instead, which is shorter and admits nothing by omission: <b>every import in the package is
 * {@code java.*}</b>, or a type of the package itself.
 *
 * <p><b>Why a source scan, in the style of {@code AntiCorruptionLayerGuardTest}.</b> The compiler
 * cannot express "this package may import only from the JDK", and a dependency rule enforced by
 * review is a rule enforced until the reviewer is busy. ADR 0006's first verification check is a
 * {@code grep} over the same directory; this is that grep as a test, so that it runs on every build
 * rather than when somebody remembers it.
 *
 * <p>The scan must find at least the two types ADR 0006 moved. An empty directory would make the
 * assertion vacuously true, which is the opposite of what a guard is for.
 */
@DisplayName("ADR 0006: the insight domain package imports java.* and nothing else (spec-01 s2.2)")
class InsightDomainDependencyRuleTest {

    private static final Path DOMAIN_SOURCES =
            Path.of("src", "main", "java", "com", "freshflow", "insight", "domain");

    private static final String DOMAIN_PACKAGE = "com.freshflow.insight.domain";

    /** An import statement, with or without {@code static}, up to the imported name. */
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;",
                    Pattern.MULTILINE);

    @Test
    @DisplayName("every import under com.freshflow.insight.domain is java.* or the package itself")
    void domainSources_importOnlyTheJdkAndThemselves() {
        List<Path> sources = domainSourceFiles();

        assertThat(sources)
                .as("the scan must actually have found the domain package at %s; ADR 0006 moved "
                        + "RiskAssessmentPolicy and BatchRiskView into it, so an empty list means "
                        + "the scan is looking in the wrong place and every assertion below would "
                        + "be vacuously true",
                        DOMAIN_SOURCES.toAbsolutePath())
                .extracting(path -> path.getFileName().toString())
                .contains("RiskAssessmentPolicy.java", "BatchRiskView.java");

        List<String> breaches = new ArrayList<>();
        for (Path source : sources) {
            String code = stripComments(read(source));

            Matcher packageDeclaration =
                    Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(code);
            if (packageDeclaration.find()
                    && !DOMAIN_PACKAGE.equals(packageDeclaration.group(1))) {
                breaches.add(source + " declares package " + packageDeclaration.group(1));
            }

            Matcher imports = IMPORT.matcher(code);
            while (imports.find()) {
                String imported = imports.group(1);
                if (!isPermitted(imported)) {
                    breaches.add(source + " imports " + imported);
                }
            }
        }

        assertThat(breaches)
                .as("spec-01 s2.2 and ADR 0006: the domain package depends on nothing -- not "
                        + "presentation, application or infrastructure, not Spring, JPA, Jackson, "
                        + "Kafka or LangChain4j. Only java.* and the package's own types")
                .isEmpty();
    }

    // --------------------------------------------------------------------------------- helpers

    private static boolean isPermitted(String imported) {
        return imported.startsWith("java.") || imported.startsWith(DOMAIN_PACKAGE + ".");
    }

    private static List<Path> domainSourceFiles() {
        if (!Files.isDirectory(DOMAIN_SOURCES)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(DOMAIN_SOURCES)) {
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
     * Removes line and block comments, leaving string literals intact, so that a Javadoc sentence
     * naming a forbidden package in order to forbid it is not mistaken for an import of it. The
     * same small state machine {@code AntiCorruptionLayerGuardTest} uses, for the same reason.
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
