Feature: Positive test - Child is intimated - Child intimates parent
Scenario: Create a new Process
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

Scenario: Retrieve the Process that just got created
When I GET a REST request to URL "/process/${id}"
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "${currentState}"

 Scenario: Send the splitDone event to the Process with comments
 Given that "comment" equals "Comment for splitDone"
 And that "event" equals "splitDone"
  And that "childId" equals "child1"
When I PATCH a REST request to URL "/process/${id}/${event}" with payload
"""json
{
    "comment": "${comment}",
    "subProcesses": [
      {"args": "filename=f1", "childId":  "${childId}", "processType":  "file", "leaf": true}
    ]
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.id" is "${id}"
And the REST response key "mutatedEntity.currentState.stateId" is "SPLITTING_AND_WAITING_SUBPROCESSES"
And store "$.payload.mutatedEntity.currentState.stateId" from response to "finalState"


Scenario: Send the doneSuccessfully event to the child
Given that "comment" equals "Comment for doneSuccessfully"
And that "event" equals "doneSuccessfully"
When I PATCH a REST request to URL "/process/${childId}/${event}" with payload
"""json
{
    "comment": "${comment}"
}
"""
Then the REST response contains key "mutatedEntity"
And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"

 Scenario: Retrieve the parent. It should be Finalizer pending
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
  And the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"