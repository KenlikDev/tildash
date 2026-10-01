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

For user-visible behavior, add automated UI coverage for important interactions. When a runnable GUI environment is available, also launch the real application and exercise the affected path through actual controls such as clicks, text input, navigation, and restart. If GUI execution is unavailable, record that limitation explicitly and do not claim manual UI verification.
