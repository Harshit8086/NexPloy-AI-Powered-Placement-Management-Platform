package com.nexploy.campuslink.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The vector literal fed to {@code CAST(:embedding AS vector)} must be pgvector's exact text form —
 * "[v0,v1,...]" with no spaces — or the cast fails at write time. This locks that formatting.
 */
class EmbeddingServiceTest {

    @Test
    void formatsPgvectorLiteral() {
        assertThat(EmbeddingService.toVectorLiteral(List.of(0.1, -0.2, 0.3)))
            .isEqualTo("[0.1,-0.2,0.3]");
    }

    @Test
    void nullOrEmptyVectorHasNoLiteral() {
        assertThat(EmbeddingService.toVectorLiteral(null)).isNull();
        assertThat(EmbeddingService.toVectorLiteral(List.of())).isNull();
    }
}
