# SharedPreferencesUsageDetector

## Problem

This linter detects the usage of `SharedPreferences` in Android code.
`SharedPreferences` is discouraged for new development in favor of
`ProtoDatastore`.

## Explanation

`ProtoDatastore` offers several advantages over `SharedPreferences`, including:

*   Clear API contract defined (`getData()` and `updateData()`)
*   Schema in form protocol buffer.
*   Async APIs
*   Durable
*   Thread-safe and supports transactions.

## Solution

For new features, use `ProtoDatastore` instead of `SharedPreferences`. You can
leverage `GuavaDatastore.java` which provides a convenient wrapper around
`ProtoDatastore`.

For existing code that uses `SharedPreferences`, you can suppress this lint
warning.

### Example

The following code will trigger this lint warning:

```java
SharedPreferences prefs = getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
```
