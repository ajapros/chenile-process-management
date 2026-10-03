package org.chenile.orchestrator.process.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.model.Process;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Lightweight schema/serialization checks; runtime routing is covered by ingress and BDD tests. */
class ProcessStateContractTest {

    @Test void oldQueuedSnapshotsRemainReadableWithoutEmittingTheRetiredFlag() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Process process = mapper.readValue("{\"processType\":\"leaf\",\"leaf\":true,\"dormant\":false}", Process.class);
        assertTrue(process.leaf);
        assertFalse(mapper.readTree(mapper.writeValueAsString(process)).has("dormant"));
    }

    @Test void flowHasOnlyTheLeafDecisionAsItsInitialStateAndNoActivationPath() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = new ClassPathResource("org/chenile/orchestrator/process/process-states.xml").getInputStream()) {
            var elements = factory.newDocumentBuilder().parse(input).getElementsByTagName("*");
            int initialStates = 0;
            for (int index = 0; index < elements.getLength(); index++) {
                var element = (org.w3c.dom.Element) elements.item(index);
                assertNotEquals("DORMANT", element.getAttribute("id"));
                assertNotEquals("isDormant", element.getAttribute("id"));
                assertNotEquals("activate", element.getAttribute("eventId"));
                if ("true".equals(element.getAttribute("initialState"))) {
                    initialStates++;
                    assertEquals("isThisLeafNode", element.getAttribute("id"));
                }
            }
            assertEquals(1, initialStates);
        }
    }
}
