Feature: Chunk test - Chunk returns error.
Scenario: Create a new chunk process
Given that "flowName" equals "PROCESS_FLOW"
And that "initialState" equals "PROCESSED"
When I POST a REST request to URL "/process" with payload
"""json
{
    "description": "Description",
    "processType": "chunk"
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