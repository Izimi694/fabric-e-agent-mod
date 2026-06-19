package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.action.BlendedAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainRouterExecuteBlendedTest {
    final DomainRouter router = new DomainRouter();

    @Test @DisplayName("executeBlended with NONE returns false")
    void noneAction() {
        assertFalse(router.executeBlended(BlendedAction.NONE));
    }

    @Test @DisplayName("executeBlended with null returns false")
    void nullAction() {
        assertFalse(router.executeBlended(null));
    }

    @Test @DisplayName("executeBlended with valid action returns true")
    void validAction() {
        assertTrue(router.executeBlended(new BlendedAction("dig_iron", 0.8, 0)));
    }
}
