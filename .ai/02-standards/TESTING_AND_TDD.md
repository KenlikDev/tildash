# Testing and TDD Standard

The order for behavior work is:

```
Requirement
-> Acceptance Criteria
-> Test
-> RED
-> Implementation
-> GREEN
-> Refactor
-> Broader Verification
```

Tests are executable specifications, not implementation targets.

Use test-first development for deterministic:
- domain logic;
- application services;
- validation rules;
- scheduling algorithms;
- permissions;
- synchronization;
- financial calculations;
- security-sensitive behavior.

Do not rewrite tests merely to match implementation.

Test behavior, not private implementation details.

Use:
- unit tests for domain/application logic;
- integration tests for infrastructure boundaries;
- HTTP contract tests for APIs;
- UI tests for important user behavior;
- end-to-end tests for critical journeys.

Use real infrastructure tests when mocking would hide important behavior.

A green suite is valid only when the assertions remain strong and the intended behavior is actually implemented.

For every added or changed test source set, verify the exact target-specific Gradle task executes that suite. An aggregate task passing is not sufficient evidence that a target-specific suite ran. Acceptance-critical test targets must be wired into CI or explicitly documented as local-only with a concrete verification command and reason.

For Desktop/JVM Compose UI tests, use the target-supported Desktop test dependencies/runtime and execute the target-specific test task. Do not treat compilation of production UI code as evidence that the UI test itself executed.

For user-visible behavior, add automated UI coverage for important interactions. When a runnable GUI environment is available, also launch the real application and exercise the affected path through actual controls such as clicks, text input, navigation, and restart. If GUI execution is unavailable, record that limitation explicitly and do not claim manual UI verification.
