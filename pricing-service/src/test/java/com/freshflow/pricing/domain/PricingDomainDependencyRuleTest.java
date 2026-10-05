package com.freshflow.pricing.domain;

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
 * package: every import is {@code java.*} or a type of the package itself. No Spring, no JPA, no
 * Jackson, no Kafka, nothing from {@code presentation}, {@code application} or
 * {@code infrastructure}.
 *
 * <p>This is the package that holds the margin floor, the invariant the whole project is built
 * around (spec-03 &sect;3, R10). It judges a supervisor's request and the agent's recommendation by
 * one rule, and that rule has to be plain Java to be unit-tested the way it is. Same source scan
 * as {@code insight-service}'s guard, added to this service in the review of 5 October 2026, when
 * README and report were found to claim the guard for every service while only one had it.
 */
@DisplayName("spec-01 s2.2: the pricing domain package imports java.* and nothing else")
class PricingDomainDependencyRuleTest {

    private static final Path DOMAIN_SOURCES =
            Path.of("src", "main", "java", "com", "freshflow", "pricing", "domain");

    private static final String DOMAIN_PACKAGE = "com.freshflow.pricing.domain";

    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;",
                    Pattern.MULTILINE);

    @Test
    @DisplayName("every import under com.freshflow.pricing.domain is java.* or the package itself")
    void domainSources_importOnlyTheJdkAndThemselves() {
        List<Path> sources = domainSourceFiles();

        assertThat(sources)
                .as("the scan must actually have found the domain package at %s",
                        DOMAIN_SOURCES.toAbsolutePath())
                .extracting(path -> path.getFileName().toString())
                .contains("MarkdownPlan.java", "MarginFloorPolicy.java", "PriceRule.java");

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
                .as("spec-01 s2.2: the domain package depends on nothing -- not presentation, "
                        + "application or infrastructure, not Spring, JPA, Jackson or Kafka. Only "
                        + "java.* and the package's own types")
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

    /** Removes line and block comments, leaving string literals intact. */
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
