# Illustrative requirements, not executed tests: no Cucumber runner is configured.
# Replace these with scenarios for the requested change and implement them in existing runners.
Feature: Consistent instance registration
  An operator can register a remote instance in demo and production modes
  without duplicating registrations or assuming ownership of a remote container.

  Scenario Outline: Register a remote LDES server
    Given the application is in <mode> mode
    And no LDES server is registered at "https://ldes.example.org"
    When the operator registers "Example server" at "https://ldes.example.org/"
    Then one registered instance is shown at "https://ldes.example.org"
    And its origin is REGISTERED
    And it does not have a local container runtime reference

    Examples:
      | mode       |
      | demo       |
      | production |

  Scenario: Reject a duplicate registration through the API
    Given an LDES server is registered at "https://ldes.example.org"
    When a client registers another LDES server at "https://ldes.example.org/"
    Then the API responds with status 409
    And the response is a problem detail
    And no second instance is stored

  Scenario: A dry sandbox run does not send an HTTP output
    Given a valid sandbox pipeline with an HTTP output
    And executeOutputs is false
    When a member is run through the pipeline
    Then the output is reported as a dry run
    And no HTTP output request is sent
