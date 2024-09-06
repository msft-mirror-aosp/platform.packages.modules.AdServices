This documentation directory contains files with explanatory information for the linter rules.

To add a new lint checker, place the detector code in the appropriate package based on whether
the checker should run against production code (prod), test code (test), or both (common). All
common Issues need to be added to *both* Issue Registry classes. Tests for the linters are not
split out by package.
