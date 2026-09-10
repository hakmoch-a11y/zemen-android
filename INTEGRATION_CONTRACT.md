# ZEMEN AI Mobile Integration Contract

The mobile host integrates Zemen AI once. The Dashboard owns the action catalog; the host application owns navigation and UI.

## Runtime endpoints

- `GET /sdk/config` — retrieve the current application configuration and active actions.
- `POST /sdk/ask` — send customer natural-language text.
- `POST /sdk/actions/validate` — validate an action proposal against the live application catalog.

All SDK requests authenticate with `X-Api-Key` using the application key issued by the Dashboard.

## Android SDK API

Preferred host integration:

- `ZemenAI.initialize(context, apiKey, baseUrl)`
- `ZemenAI.openChat(context)`
- `ZemenAI.setActionHandler { action -> ... }`

`getChatIntent()` remains available for backward compatibility. `registerAction()` remains available only for backward compatibility and is deprecated for new integrations.

## Generic action bridge

The SDK never needs to know the host application's Activity, Fragment, Compose route, navigation library, or screen names.

The SDK emits exactly one validated generic event:

```kotlin
ZemenAI.setActionHandler { action ->
    existingHostDispatcher.handleZemenAction(action)
}
```

The host dispatcher receives:

- `action.id`
- `action.name`
- `action.route`
- `action.parameters`
- `action.metadata`

The host app then invokes its own existing navigation/UI/business layer. This is the only app-specific bridge required.

## Dashboard responsibilities

The Dashboard does **not** ask the developer to configure every action separately. Step 5 configures one host action bridge and shows a read-only preview of the active actions for that application.

The developer does not map `bank_transfer`, `buy_airtime`, or any other specific action in the Zemen SDK. Those are application data configured by the bank administrator and delivered at runtime.

## Security boundary

The AI provider credential never belongs in the mobile app. The mobile app receives only its own application API key and Gateway URL.

## UI thread guarantee

`ZemenAI.setActionHandler` delivers the generic action callback on Android's main thread, so host navigation/UI code can be called directly without `runOnUiThread`, `Handler`, or architecture-specific threading code.
