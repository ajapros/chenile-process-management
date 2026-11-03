Feature: Process outbox polling with a database per tenant
  Each tenant has its own database and its own process outbox. These scenarios never drain the
  outbox themselves: a process completes only if the background poller reaches its tenant.

Scenario: Processes created for two tenants both complete
When I construct a REST request with header "x-chenile-tenant-id" and value "tenant1"
And I POST a REST request to URL "/process" with payload
"""json
{
    "description": "tenant1 chunk",
    "processDefName": "chunk"
}
"""
Then success is true
And store "$.payload.mutatedEntity.id" from response to "tenant1ProcessId"
When I construct a REST request with header "x-chenile-tenant-id" and value "tenant2"
And I POST a REST request to URL "/process" with payload
"""json
{
    "description": "tenant2 chunk",
    "processDefName": "chunk"
}
"""
Then success is true
And store "$.payload.mutatedEntity.id" from response to "tenant2ProcessId"
And the process outbox of tenant "tenant1" is drained within 10 seconds
And the process outbox of tenant "tenant2" is drained within 10 seconds
When I GET a REST request to URL "/process/${tenant2ProcessId}"
Then the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"
And the REST response key "mutatedEntity.description" is "tenant2 chunk"
When I construct a REST request with header "x-chenile-tenant-id" and value "tenant1"
And I GET a REST request to URL "/process/${tenant1ProcessId}"
Then the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"
And the REST response key "mutatedEntity.description" is "tenant1 chunk"
When I GET a REST request to URL "/process/${tenant2ProcessId}"
Then the http status code is 404

Scenario: A process created without a tenant completes in the default tenant
When I POST a REST request to URL "/process" with payload
"""json
{
    "description": "default tenant chunk",
    "processDefName": "chunk"
}
"""
Then success is true
And store "$.payload.mutatedEntity.id" from response to "defaultTenantProcessId"
And the process outbox of tenant "tenant1" is drained within 10 seconds
When I construct a REST request with header "x-chenile-tenant-id" and value "tenant1"
And I GET a REST request to URL "/process/${defaultTenantProcessId}"
Then the REST response key "mutatedEntity.currentState.stateId" is "PROCESSED"
When I construct a REST request with header "x-chenile-tenant-id" and value "tenant2"
And I GET a REST request to URL "/process/${defaultTenantProcessId}"
Then the http status code is 404
