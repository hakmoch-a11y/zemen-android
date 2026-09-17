package com.zemenai.sdk

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.zemenai.sdk.core.ZemenAiCore
import com.zemenai.sdk.core.actions.ActionHandler
import com.zemenai.sdk.core.model.ActionOutcome
import com.zemenai.sdk.core.model.ActionProposal
import com.zemenai.sdk.core.model.ChatTurn
import com.zemenai.sdk.core.model.ZemenConfig
import com.zemenai.sdk.core.network.ZemenApiException
import com.zemenai.sdk.network.AndroidHttpTransport
import com.zemenai.sdk.ui.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Generic Android integration facade.
 *
 * The host application configures Zemen AI once. Actions, parameters and
 * routes come from the Dashboard/Gateway at runtime. No NavController,
 * Compose, Fragment, Activity or per-action SDK registration is required.
 */
object ZemenAI {
    private const val DEFAULT_BASE_URL = ""
    private const val PREFS = "zemen_ai_sdk"
    private const val CONFIG_JSON = "config_json"
    private const val CONFIG_VERSION = "config_version"
    private const val CONFIG_TIMESTAMP = "config_timestamp"
    private const val CONFIG_EXPIRATION = "config_expiration"
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    @Volatile private var core: ZemenAiCore? = null
    @Volatile private var apiKey: String? = null
    @Volatile private var cachedConfig: ZemenConfig? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: Context, apiKey: String, baseUrl: String = DEFAULT_BASE_URL) {
        require(apiKey.isNotBlank()) { "ZemenAI.initialize: apiKey must not be blank" }
        require(baseUrl.isNotBlank()) { "ZemenAI.initialize: baseUrl must not be blank" }
        this.apiKey = apiKey
        core = ZemenAiCore(
            baseUrl = baseUrl.trimEnd('/'),
            apiKey = apiKey,
            transport = AndroidHttpTransport(),
        )

        val appContext = context.applicationContext
        restoreCachedConfig(appContext)
        // Best-effort background refresh. Failure never prevents the host app
        // from starting or opening the chat screen.
        scope.launch {
            runCatching { refreshConfig(appContext) }
        }
    }

    /**
     * Convenience alias for the recommended host-app navigation boundary.
     * The host app remains responsible for interpreting the route and opening
     * its own existing UI/business flow.
     */
    fun setHostRouter(router: ZemenHostRouter) {
        setActionHandler(ZemenActionHandler { action -> router.open(action) })
    }

    /** Installs one handler for every validated action. */
    fun setActionHandler(handler: ZemenActionHandler) {
        requireCore().setActionHandler { action ->
            val event = ZemenAction(
                id = action.id,
                name = action.name,
                route = action.route,
                parameters = action.parameters,
                metadata = action.metadata,
            )
            // UI/navigation callbacks are delivered on Android's main thread so
            // host applications do not need architecture-specific thread plumbing.
            mainHandler.post { handler.handle(event) }
        }
    }

    fun clearActionHandler() = requireCore().setActionHandler(null)

    @Deprecated("Use setActionHandler() for Dashboard-driven generic actions")
    fun registerAction(name: String, handler: ActionHandler) = requireCore().registerAction(name, handler)

    fun unregisterAction(name: String) = requireCore().unregisterAction(name)

    suspend fun ask(question: String): ChatTurn = requireCore().sendMessage(question)

    suspend fun handleAction(proposal: ActionProposal): ActionOutcome = requireCore().handleAction(proposal)

    suspend fun refreshConfig(context: Context): ZemenConfig {
        val config = requireCore().loadConfig()
        cachedConfig = config
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CONFIG_JSON, config.toJson())
            .putLong(CONFIG_VERSION, config.configurationVersion)
            .putLong(CONFIG_TIMESTAMP, System.currentTimeMillis())
            .putLong(CONFIG_EXPIRATION, System.currentTimeMillis() + CACHE_TTL_MS)
            .apply()
        return config
    }

    /** Returns the last valid remote configuration. Expired entries are never returned. */
    fun getCachedConfig(context: Context): ZemenConfig? {
        if (cachedConfig != null) {
            val expiration = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CONFIG_EXPIRATION, 0L)
            return cachedConfig.takeIf { expiration == 0L || expiration > System.currentTimeMillis() }
        }
        restoreCachedConfig(context.applicationContext)
        return cachedConfig
    }

    fun clearCachedConfig(context: Context) {
        cachedConfig = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Preferred built-in chat entry point — works from any Android UI architecture. */
    fun openChat(context: Context) {
        val intent = getChatIntent(context)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Backwards-compatible low-level entry point. */
    fun getChatIntent(context: Context): Intent = Intent(context, ChatActivity::class.java)

    internal fun requireCore(): ZemenAiCore = core
        ?: throw ZemenApiException.ConfigurationError(
            "ZemenAI.initialize(context, apiKey) must be called before using the SDK. " +
                "Common cause: the Application class that calls initialize() isn't registered " +
                "with android:name in AndroidManifest.xml, so it never ran."
        )

    private fun restoreCachedConfig(context: Context) {
        if (cachedConfig != null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expiration = prefs.getLong(CONFIG_EXPIRATION, 0L)
        if (expiration != 0L && expiration <= System.currentTimeMillis()) return
        val raw = prefs.getString(CONFIG_JSON, null) ?: return
        runCatching {
            cachedConfig = ZemenConfig.fromJson(
                com.zemenai.sdk.core.json.JsonParser.parse(raw) as com.zemenai.sdk.core.json.JsonValue.JsonObject
            )
        }
    }
}
