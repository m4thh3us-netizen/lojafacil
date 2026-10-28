package com.lojafacil.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColorContrastTest {
    @Test
    void escolheTextoComMaisContraste() {
        assertThat(ColorContrast.bestTextOn("#1F5EFF")).isEqualTo(ColorContrast.LIGHT_TEXT);
        assertThat(ColorContrast.bestTextOn("#FDE68A")).isEqualTo(ColorContrast.DARK_TEXT);
    }

    @Test
    void contrasteMaximoEntrePretoEBranco() {
        assertThat(ColorContrast.ratio("#000000", "#FFFFFF")).isBetween(20.9, 21.1);
    }

    @Test
    void coresPadraoSaoLegiveis() {
        assertThat(ColorContrast.isReadable(Store.DEFAULT_PRIMARY)).isTrue();
        assertThat(ColorContrast.isReadable(Store.DEFAULT_SECONDARY)).isTrue();
    }
}
