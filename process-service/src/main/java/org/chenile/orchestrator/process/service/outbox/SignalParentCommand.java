package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessDoneWithErrorsPayload;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import java.util.List;
import org.chenile.orchestrator.process.model.Process;
import org.apache.commons.lang3.StringUtils;
import java.util.stream.Collectors;

public class SignalParentCommand extends AbstractProcessOutboxCommand {
    public static final String TYPE = "SIGNAL_PARENT";
    /** All addressing and event-specific data is in this JSON object, not table columns. */
    public record Data(String parentId, String childId, String eventName, JsonNode payload) { }
    public SignalParentCommand(ProcessCommandSupport support) { super(support); }
    @Override public String type() { return TYPE; }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        var process = transition.process();
        if (!terminal(transition) || process.parentId == null) return;
        Object payload;
        String event;
        if (Constants.States.PROCESSED.equals(transition.state().getStateId())) {
            DoneSuccessfullyPayload done = new DoneSuccessfullyPayload(); done.childId = process.getId();
            payload = done; event = Constants.Events.SUB_PROCESS_DONE_SUCCESSFULLY;
        } else {
            SubProcessDoneWithErrorsPayload failed = errorPayload(process);
            failed.childId = process.getId(); payload = failed; event = Constants.Events.SUB_PROCESS_DONE_WITH_ERRORS;
        }
        store(process, "signal:" + process.parentId + ":" + process.getId() + ":" + event,
                new Data(process.parentId, process.getId(), event, support.mapper.valueToTree(payload)));
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        Data data = read(entry, Data.class);
        String receipt = support.mapper.writeValueAsString(List.of(data.parentId(), data.childId()));
        if (!support.receive("PARENT", receipt)) return;
        Object payload;
        if (Constants.Events.SUB_PROCESS_DONE_SUCCESSFULLY.equals(data.eventName()))
            payload = support.mapper.treeToValue(data.payload(), DoneSuccessfullyPayload.class);
        else if (Constants.Events.SUB_PROCESS_DONE_WITH_ERRORS.equals(data.eventName()))
            payload = support.mapper.treeToValue(data.payload(), SubProcessDoneWithErrorsPayload.class);
        else throw new IllegalArgumentException("Invalid parent signal event: " + data.eventName());
        support.managers.getObject().processById(data.parentId(), data.eventName(), payload);
    }

    private SubProcessDoneWithErrorsPayload errorPayload(Process process) {
        SubProcessDoneWithErrorsPayload payload = new SubProcessDoneWithErrorsPayload();
        if (process.errors == null || process.errors.isEmpty()) return payload;
        payload.errors = process.errors.stream().flatMap(error -> error.errors.stream()).collect(Collectors.toList());
        process.errors.stream().filter(error -> StringUtils.isNotBlank(error.exceptionMessage)).findFirst()
                .ifPresent(error -> { payload.exceptionMessage = error.exceptionMessage; payload.stackTrace = error.stackTrace; });
        return payload;
    }
}
