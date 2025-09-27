Feature: Negative test - SubProcess has errors - Parent is intimated
Scenario: Create a new process
Given that "flowName" equals "PROCESS_FLOW"
And that "initialState" equals "SPLITTING_AND_WAITING_SUBPROCESSES"
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

Scenario: Retrieve the file that just got created
When I GET a REST request to URL "/process/${id}"
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "${currentState}"

Scenario: Send the splitDone event to the file with comments
Given that "comment" equals "Comment for splitDone"
And that "event" equals "splitDone"
When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "subProcesses": [
      {"args": "filename=f1","childId": "child29", "processType":  "file"}
    ]
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "SPLITTING_AND_WAITING_SUBPROCESSES"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"

Scenario: Send the subProcessDoneWithErrors event to the file with comments
Given that "comment" equals "Comment for subProcessDoneWithErrors"
And that "event" equals "subProcessDoneWithErrors"
When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "errors": [{
    "processId": "child29",
     "errors": ["row1 was not structured properly"]
     }]
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "AGGREGATION_PENDING"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"

Scenario: Send the aggregationDone event to the file with comments
  Given that "comment" equals "Comment for aggregationDone"
  And that "event" equals "aggregationDone"
  When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
  "comment": "${comment}"
}
"""
  Then the REST response contains key "mutatedEntity"
  And the REST response key "mutatedEntity.id" is "${id}"
  And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED_WITH_ERRORS"
  And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"