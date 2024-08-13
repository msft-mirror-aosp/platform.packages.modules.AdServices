This documentation directory contains files with explanatory information for the linter rules.

# Adding a New Linter

To add a new lint checker, place the detector code in the appropriate package based on whether the
checker should run against production code (prod), test code (test), or both (common). All common
Issues need to be added to *both* Issue Registry classes. Tests for the linters are not split out by
package.

# Testing

1. Add appropriate test cases in the `tests` directory.

2. Trigger the linter on a specific target locally
   e.g. `m AdServicesServiceCoreTopicsUnitTests-lint`.
    1. To debug further and see a complete report of all the linters that ran,
       open `lint-report.html`, which can be found
       under `out/soong/.intermediates/{PATH_TO_TARGET}/android_common/lint/`.

3. Post a CL to verify linter is triggering on Gerrit.

**NOTE:** Due to b/358643466, linter will not trigger on all targets. If you would like to see the
linter in action for a specific target impacted by the bug, workaround (only for testing purposes)
is to list the following explicitly in the target's `Android.bp` file:

```
lint: {
            extra_check_modules: ["AdServicesTestLintChecker"],
            test: false, // TODO(b/343741206): remove when checks will run on android_test
     },
```
