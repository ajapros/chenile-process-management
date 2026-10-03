package org.chenile.orchestrator.process.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.PredecessorArgs;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the input/output envelope passed from a completed process to its successors. */
public final class CompletionArguments {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CompletionArguments() { }

    public static Map<String, Object> from(String input, String output) {
        return from(input, output, PredecessorArgs.BOTH);
    }

    public static Map<String, Object> from(String input, String output, PredecessorArgs selection) {
        if (selection == null) selection = PredecessorArgs.BOTH;
        Map<String, Object> args = new LinkedHashMap<>();
        if (selection != PredecessorArgs.OUTPUT) args.put("input", decode(input));
        if (selection != PredecessorArgs.INPUT) args.put("output", decode(output));
        return args;
    }

    private static Object decode(String value) {
        if (value == null) return null;
        try { return MAPPER.readValue(value, Object.class); }
        catch (JsonProcessingException exception) { return value; }
    }
}
