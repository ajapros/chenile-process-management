Feature: Manage crontabs through the Chenile service controller

  Background:
    Given I construct a REST request with header "x-chenile-tenant-id" and value "alpha"
    And I construct a REST request with header "x-chenile-auth-user" and value "scheduler-admin"

  Scenario: Save, retrieve, list, disable, and delete a schedule
    When I POST a REST request to URL "/crontab" with payload
      """
      {"name":"nightly","cronExpression":"0 0 0 1 1 ? 2099","eventName":"CustomerEvent",
       "eventPayloadJson":"not parsed by the cron service","headersJson":"{\"x-request-id\":\"batch-1\"}"}
      """
    Then success is true
    And the http status code is 200
    And store "$.payload.id" from response to "id"
    And the REST response key "tenant" is "alpha"
    And the REST response key "createdBy" is "scheduler-admin"
    And the REST response key "eventPayloadJson" is "not parsed by the cron service"
    And Quartz has scheduled the saved crontab
    And no trigger has been invoked
    When I GET a REST request to URL "/crontab/${id}"
    Then success is true
    And the REST response key "id" is "${id}"
    When I GET a REST request to URL "/crontab"
    Then the REST expression "payload" size is 1
    When I POST a REST request to URL "/crontab" with payload
      """
      {"id":"${id}","name":"nightly","cronExpression":"0 0 0 1 1 ? 2099","eventName":"CustomerEvent","enabled":false}
      """
    Then success is true
    And the value of "$.payload.enabled" is "false"
    And Quartz has removed the saved crontab schedule
    When I DELETE a REST request to URL "/crontab/${id}" with payload
      """
      {}
      """
    Then success is true
    When I GET a REST request to URL "/crontab/${id}"
    Then the http status code is 404
    And success is false
    When I GET a REST request to URL "/crontab"
    Then the REST response is empty

  Scenario Outline: Invalid schedules return Chenile errors and are never persisted
    When I POST a REST request to URL "/crontab" with payload
      """
      {"name":"invalid","cronExpression":"<expression>","timezone":"<timezone>","eventName":"CustomerEvent","headersJson":"<headers>"}
      """
    Then the http status code is 400
    And success is false
    When I GET a REST request to URL "/crontab"
    Then the REST response is empty
    And no trigger has been invoked
    Examples:
      | expression           | timezone  | headers                    |
      | invalid              | UTC       | {}                         |
      | 0 0 0 1 1 ? 2099      | Mars/Base | {}                         |
      | 0 0 0 1 1 ? 2099      | UTC       | {\"custom\":\"value\"}     |

  Scenario: Other tenants cannot read, overwrite, or delete a schedule
    When I POST a REST request to URL "/crontab" with payload
      """
      {"name":"private","cronExpression":"0 0 0 1 1 ? 2099","eventName":"CustomerEvent"}
      """
    Then success is true
    And store "$.payload.id" from response to "id"
    When I construct a REST request with header "x-chenile-tenant-id" and value "beta"
    And I GET a REST request to URL "/crontab"
    Then the REST response is empty
    When I GET a REST request to URL "/crontab/${id}"
    Then the http status code is 404
    When I POST a REST request to URL "/crontab" with payload
      """
      {"id":"${id}","name":"private","cronExpression":"0 0 0 1 1 ? 2099","eventName":"CustomerEvent"}
      """
    Then the http status code is 404
    When I DELETE a REST request to URL "/crontab/${id}" with payload
      """
      {}
      """
    Then the http status code is 404
    When I POST a REST request to URL "/crontab" with payload
      """
      {"name":"spoofed","tenant":"alpha","cronExpression":"0 0 0 1 1 ? 2099","eventName":"CustomerEvent"}
      """
    Then the http status code is 400
    When I construct a REST request with header "x-chenile-tenant-id" and value "alpha"
    And I GET a REST request to URL "/crontab/${id}"
    Then success is true
    And the REST response key "tenant" is "alpha"
