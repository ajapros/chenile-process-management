Feature: Manage global process definitions through the Chenile service

  Background:
    Given I construct a REST request with header "x-chenile-tenant-id" and value "alpha"

  Scenario: Create, inspect, and replace definitions without enabling the management dashboard
    Given the runtime definition "file" is absent
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"file","leaf":true,"args":"example.FileInput","config":{"queue":"files","chunkSize":"10"}}
      """
    Then success is true
    And the http status code is 201
    And the value of "$.code" is "201"
    And the REST response key "processType" is "file"
    And the runtime definition "file" has config "chunkSize" equal to "10"
    When I GET a REST request to URL "/process-definitions/file"
    Then success is true
    And the REST response key "args" is "example.FileInput"
    When I GET a REST request to URL "/process-definitions"
    Then the REST expression "payload" size is 1
    When I PUT a REST request to URL "/process-definitions/file" with payload
      """
      {"leaf":true,"config":{"queue":"files","chunkSize":"20"}}
      """
    Then success is true
    And the http status code is 200
    And the REST response key "processType" is "file"
    And the runtime definition "file" has config "chunkSize" equal to "20"
    When I construct a REST request with header "x-chenile-tenant-id" and value "beta"
    And I GET a REST request to URL "/process-definitions/file"
    Then success is true
    And the REST response key "config.chunkSize" is "20"

  Scenario: Creation conflicts and missing definitions have distinct errors
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"file","leaf":true}
      """
    Then success is true
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"file","leaf":false}
      """
    Then the http status code is 409
    And success is false
    When I GET a REST request to URL "/process-definitions/file"
    Then the value of "$.payload.leaf" is "true"
    When I GET a REST request to URL "/process-definitions/unknown"
    Then the http status code is 404
    When I PUT a REST request to URL "/process-definitions/unknown" with payload
      """
      {"processType":"unknown","leaf":true}
      """
    Then the http status code is 404

  Scenario Outline: Invalid definitions do not become effective
    When I POST a REST request to URL "/process-definitions" with payload
      """
      <definition>
      """
    Then the http status code is 400
    And success is false
    When I GET a REST request to URL "/process-definitions"
    Then the REST response is empty
    Examples:
      | definition                                                       |
      | {}                                                               |
      | {"processType":"file","predecessorArgs":null}                      |
      | {"processType":"file","config":null}                               |
      | {"processType":"file","predecessorProcessType":"file"}              |
      | {"processType":"file","parentProcessType":"file"}                   |

  Scenario: URL identity and parent/predecessor relationships are validated
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"first","parentProcessType":"second","predecessorProcessType":"second"}
      """
    Then success is true
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"second","parentProcessType":"first"}
      """
    Then the http status code is 400
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"second","predecessorProcessType":"first"}
      """
    Then the http status code is 400
    When I PUT a REST request to URL "/process-definitions/first" with payload
      """
      {"processType":"different"}
      """
    Then the http status code is 400
    When I GET a REST request to URL "/process-definitions"
    Then the REST expression "payload" size is 1

  Scenario: Rolled-back updates leave persisted and cached definitions unchanged
    When I POST a REST request to URL "/process-definitions" with payload
      """
      {"processType":"file","leaf":true,"config":{"chunkSize":"10"}}
      """
    Then success is true
    And the runtime definition "file" has config "chunkSize" equal to "10"
    When an update to definition "file" with config "chunkSize" equal to "99" is rolled back
    Then the runtime definition "file" has config "chunkSize" equal to "10"
    When I GET a REST request to URL "/process-definitions/file"
    Then the REST response key "config.chunkSize" is "10"
