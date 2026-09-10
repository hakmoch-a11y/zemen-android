# Zemen AI Android SDK

The Zemen AI Android SDK is a thin client for third-party mobile applications. The host app keeps ownership of navigation, screens, and business logic. Zemen AI delivers validated generic actions to one host callback.

## Recommended integration

Publish the Android artifact to a Maven-compatible repository and integrate it as one dependency:

```kotlin
dependencies {
    implementation("com.zemenai:zemen-ai-android:1.0.0")
}
```

The repository URL and version are deployment/configuration values supplied by Zemen Dashboard. The source-module project remains available for local development and fallback distribution.

## API

```kotlin
ZemenAI.initialize(
    context = this,
    apiKey = BuildConfig.ZEMEN_API_KEY,
    baseUrl = BuildConfig.ZEMEN_GATEWAY_URL,
)

ZemenAI.setHostRouter { action ->
    ExistingAppRouter.handle(action)
}

ZemenAI.openChat(context)
```

`setActionHandler(...)` remains available. `getChatIntent()` remains available for backward compatibility.

## Generic action contract

The SDK receives active application actions from the Zemen Gateway. A validated action contains:

- `id`
- `name`
- `route`
- `parameters`
- `metadata`

The SDK does not hardcode bank actions. The host app decides how a route enters its own UI/business flow.

## Security

The SDK receives only the application's scoped Zemen API key. Provider credentials, database credentials, and AI-service private credentials must never be shipped to the mobile app.

## Distribution

`android/publishing.gradle.kts` contains Maven publication scaffolding. Actual publication requires a configured Maven repository, credentials/signing where applicable, and a real release tag/version.
