Feature: Test Complex Create
  Scenario: Create a new Feed process with a long JSON
    Given that "flowName" equals "PROCESS_FLOW"
    And that "initialState" equals "SPLITTING_AND_WAITING_SUBPROCESSES"
    And that "description" equals "sample-description"
    When I POST a REST request to URL "/process" with payload
"""json
{
  "id":null,
  "createdTime":null,
  "lastModifiedTime":null,
  "lastModifiedBy":null,
  "tenant":null,
  "createdBy":null,
  "testEntity":false,
  "version":0,
  "stateEntryTime":null,
  "slaYellowDate":null,
  "slaRedDate":null,
  "slaTendingLate":0,
  "slaLate":0,
  "leaf":false,
  "dormant":false,
  "clientId":null,
  "processType":"foo",
  "splitCompleted":false,
  "completedPercent":0,
  "parentId":null,
  "childIdToActivateSuccessors":null,
  "description":"${description}",
  "errors":[],
  "numSubProcesses":0,
  "numCompletedSubProcesses":0,
  "subProcesses":[],
  "args":null,
  "predecessorId":null,
  "initializedStates":[],
  "currentState":null
}
"""
    Then the REST response contains key "mutatedEntity"
    And store "$.payload.mutatedEntity.id" from response to "id"
    And the REST response key "mutatedEntity.currentState.stateId" is "${initialState}"
    And store "$.payload.mutatedEntity.currentState.stateId" from response to "currentState"
    And the REST response key "mutatedEntity.description" is "${description}"
