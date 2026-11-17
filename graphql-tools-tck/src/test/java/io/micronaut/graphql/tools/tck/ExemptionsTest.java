package io.micronaut.graphql.tools.tck;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExemptionsTest {

    @Test
    void exemptScenariosAreNotRunAndKeepTheirReason() {
        Map<String, String> exempt = Tck.exemptions();
        assertEquals(Map.of("arguments", "the fixtures of this suite are not written yet"), exempt);
        List<String> ids = Tck.scenarioIds();
        assertFalse(ids.contains("arguments"));
        assertEquals(Scenarios.ids().size() - 1, ids.size());
    }

}
