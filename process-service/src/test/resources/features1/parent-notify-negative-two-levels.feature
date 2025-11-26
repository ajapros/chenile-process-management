Feature: Two Level Negative test - Grandchild is intimated
Scenario: Create a new Process
Given that "flowName" equals "PROCESS_FLOW"
And that "initialState" equals "SPLIT_PENDING"
When I POST a REST request to URL "/process" with payload
"""json
{
    "description": "Description",
    "processType": "feed"
}
"""
Then the REST response contains key "mutatedEntity"
And store "$.payload.mutatedEntity.id" from response to "id"
And the REST response key "mutatedEntity.currentState.stateId" is "${initialState}"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "currentState"
And the REST response key "mutatedEntity.description" is "Description"

Scenario: Retrieve the Process that just got created
When I GET a REST request to URL "/process/${id}"
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "${currentState}"

 Scenario: Send the splitDone event to the Process with comments
 Given that "comment" equals "Comment for splitDone"
 And that "event" equals "splitDone"
  And that "childId" equals "child5"
When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "subProcesses": [
      {"args": "filename=f1", "childId":  "${childId}", "processType":  "file"}
    ]
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "SUB_PROCESSES_PENDING"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"

 Scenario: Send the splitDone event to the child process
  Given that "comment" equals "Comment for splitDone for child"
  And that "event" equals "splitDone"
  And that "grandChildId" equals "grandChild2"
  When I PATCH a REST request to URL "/process/${childId}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "subProcesses": [
      {"args": "filename=f1", "childId":  "${grandChildId}", "processType":  "chunk",
      "leaf":  true}
    ]
}
"""
  Then the REST response contains key "mutatedEntity"
  And the REST response key "mutatedEntity.currentState.stateId" is "SUB_PROCESSES_PENDING"


 Scenario: Send the doneWithErrors event to the grand child
 Given that "comment" equals "Comment for doneWithErrors"
 And that "event" equals "doneWithErrors"
When I PATCH a REST request to URL "/process/${grandChildId}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "errors": [ "error1","error2"]
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED_WITH_ERRORS"

 Scenario: Retrieve the first child. It should be aggregation pending
  When I GET a REST request to URL "/process/${childId}"
  Then the REST response contains key "mutatedEntity"
  And the REST response key "mutatedEntity.currentState.stateId" is "AGGREGATION_PENDING"

Scenario: Send the aggregationDone event to the Process Child with comments
 Given that "comment" equals "Comment for aggregationDone"
 And that "event" equals "aggregationDone"
 When I PATCH a REST request to URL "/process/${childId}/${event}" with payload
"""json
{
 "comment": "${comment}"
}
"""
 Then the REST response contains key "mutatedEntity"
 And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED_WITH_ERRORS"

 Scenario: Retrieve the parent. It should also be processed.
 When I GET a REST request to URL "/process/${id}"
 Then the REST response contains key "mutatedEntity"
 And the REST response key "mutatedEntity.id" is "${id}"
 And the REST response key "mutatedEntity.currentState.stateId" is "AGGREGATION_PENDING"

Scenario: Send the aggregationDone event to the Parent with comments
 Given that "comment" equals "Comment for aggregationDone"
 And that "event" equals "aggregationDone"
 When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
"comment": "${comment}"
}
"""
 Then the REST response contains key "mutatedEntity"
 And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED_WITH_ERRORS"