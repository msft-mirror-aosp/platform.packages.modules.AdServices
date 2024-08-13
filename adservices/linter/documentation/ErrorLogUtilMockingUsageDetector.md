# Context

`AdServicesLoggingUsageRule` has been introduced in `AdServicesExtendedMockitoTestCase` to make
critical logging verification simpler (and required) with the first use case
targeting `ErrorLogUtil`.

For `AdServicesLoggingUsageRule` to scan `ErrorLogUtil` usage across all test subclasses,
`ErrorLogUtil` is automatically spied and mocked appropriately to store actual invocation calls. The
test author would only need to use annotations like `@ExpectErrorLogUtilCall` and/or
`@ExpectErrorLogUtilWithExceptionCall` over test methods to verify logging and satisfy the rule.

# Problem

Test author may unknowingly silence the `AdServicesLoggingUsageRule` by mocking /
spying `ErrorLogUtil` and overriding its behavior.

We will need a linter that flags if `ErrorLogUtil` is getting mocked explicitly in tests.

# Linter Strategy

The linter detects and flags the following:

1. Usage of `@SpyStatic(ErrorLogUtil.class)` and `@MockStatic(ErrorLogUtil.class)` over classes and
   methods. These annotations are most commonly used for static mocking / spying
   in `AdServicesExtendedMockitoTestCase` subclasses.
2. Usage of `doNothingOnErrorLogUtilError()` in `AdServicesExtendedMockitoTestCase` subclasses.
3. Usage of `when(..)` with `ErrorLogUtil` as part of its parameter
   in `AdServicesExtendedMockitoTestCase` subclasses.
