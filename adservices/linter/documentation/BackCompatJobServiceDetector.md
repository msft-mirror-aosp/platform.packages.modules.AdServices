# Motivation
Rubidium Back Compat design introduces additional constraints on how JobServices can be used in AdServices codebase. Add a linter to warn engineer about the additional constraints in code review

## Lint Description
Avoid using new classes in AdServices JobService field Initializers. Due to the fact that ExtServices can OTA to any AdServices build, JobServices code needs to be properly gated to avoid NoClassDefFoundError. NoClassDefFoundError can happen when new class is used in ExtServices build, and the error happens when the device OTA to old AdServices build on T which does not contain the new class definition

# How-to
## Avoid static initialization in JobServices
It is hard (or impossible in some cases) to properly gate field Initializers with function calls. Put any new class usage after ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this) in onStartJob, and so the new class usage can be properly gated

## Classes which are safe to use in static initialization
The following class are safe to use in JobServices static initialization

* com.android.adservices.spe.AdservicesJobInfo
* com.android.adservices.concurrency.AdServicesExecutors
