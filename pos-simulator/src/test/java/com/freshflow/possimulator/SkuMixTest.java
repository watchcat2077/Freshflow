package com.freshflow.possimulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The weighted SKU draw behind {@code --skus}. No Spring and no network: the mix is a value and
 * the draw takes the {@link Random} it is given, which is what makes a seeded run repeatable.
 */
@DisplayName("pos-simulator: weighted SKU mix")
class SkuMixTest {

    private static final String DEFAULT_SPEC = "SKU-YOG-1KG:0.7,SKU-STR-250G:0.3";

    @Test
    @DisplayName("parses SKU:weight pairs in order and renders them back")
    void parsesPairsInOrder() {
        SkuMix mix = SkuMix.parse(DEFAULT_SPEC);

        assertThat(mix.skus()).containsExactly("SKU-YOG-1KG", "SKU-STR-250G");
        assertThat(mix.toString()).isEqualTo(DEFAULT_SPEC);
        assertThat(SkuMix.parse(" SKU-A : 7 , SKU-B:3 ").toString())
                .as("whitespace is tolerated and integral weights render without a fraction")
                .isEqualTo("SKU-A:7,SKU-B:3");
    }

    /** The whole point of {@code --seed}: the same seed draws the same scan sequence. */
    @Test
    @DisplayName("the same seed draws the same sequence of SKUs")
    void drawIsRepeatableFromTheSeed() {
        SkuMix mix = SkuMix.parse(DEFAULT_SPEC);
        List<String> first = draw(mix, new Random(42), 500);
        List<String> second = draw(mix, new Random(42), 500);

        assertThat(first).isEqualTo(second);
        assertThat(first).as("both SKUs come up in 500 draws").contains("SKU-YOG-1KG", "SKU-STR-250G");
    }

    /**
     * Weights are probabilities up to a constant. With a fixed seed the share is a fixed number,
     * so the tolerance is for the binomial noise of ten thousand draws, not for randomness between
     * runs.
     */
    @Test
    @DisplayName("each SKU is drawn in proportion to its weight")
    void drawFollowsTheWeights() {
        SkuMix mix = SkuMix.parse(DEFAULT_SPEC);
        List<String> draws = draw(mix, new Random(42), 10_000);

        double yoghurtShare = draws.stream().filter("SKU-YOG-1KG"::equals).count() / 10_000.0;
        assertThat(yoghurtShare).isBetween(0.67, 0.73);

        // Relative, not absolute: 7 and 3 are the same mix as 0.7 and 0.3.
        List<String> relative = draw(SkuMix.parse("SKU-YOG-1KG:7,SKU-STR-250G:3"), new Random(42), 10_000);
        assertThat(relative).isEqualTo(draws);
    }

    @Test
    @DisplayName("a single SKU is always the one drawn")
    void singleSkuIsAlwaysDrawn() {
        SkuMix mix = SkuMix.parse("SKU-ONLY:1");

        assertThat(draw(mix, new Random(1), 100)).containsOnly("SKU-ONLY");
    }

    @Test
    @DisplayName("a malformed mix is rejected with a message naming the problem")
    void rejectsMalformedSpecs() {
        assertThatThrownBy(() -> SkuMix.parse("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A")).hasMessageContaining("SKU:weight");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A:")).hasMessageContaining("SKU:weight");
        assertThatThrownBy(() -> SkuMix.parse(":0.5")).hasMessageContaining("SKU:weight");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A:lots")).hasMessageContaining("must be a number");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A:0")).hasMessageContaining("must be positive");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A:-1")).hasMessageContaining("must be positive");
        assertThatThrownBy(() -> SkuMix.parse("SKU-A:1,SKU-A:2")).hasMessageContaining("more than once");
    }

    private static List<String> draw(SkuMix mix, Random random, int count) {
        List<String> draws = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            draws.add(mix.pick(random));
        }
        return draws;
    }
}
