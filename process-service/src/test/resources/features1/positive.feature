Feature: Positive Test - SubProcess has terminated successfully - Parent is intimated
Scenario: Create a new file
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

Scenario: Retrieve the file that just got created
When I GET a REST request to URL "/process/${id}"
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "${currentState}"

 Scenario: Send the splitDone event to the process with comments
 Given that "comment" equals "Comment for splitDone"
 And that "event" equals "splitDone"
  And that "childId" equals "child2"
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

 Scenario: Send the subProcessDoneSuccessfully event to the file with comments
 Given that "comment" equals "Comment for subProcessDoneSuccessfully"
 And that "event" equals "subProcessDoneSuccessfully"
When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
    "comment": "${comment}"
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
 "comment": "${comment}",
  "errors": ["row1 was not structured properly"]
}
"""
 Then the REST response contains key "mutatedEntity"
 And the REST response key "mutatedEntity.id" is "${id}"
 And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"
 And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"