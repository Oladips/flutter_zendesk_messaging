import android.content.Intent
import com.chyiiiiiiiiiiiiii.zendesk_messaging.ZendeskMessagingPlugin
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import zendesk.android.Zendesk
import zendesk.android.ZendeskResult
import zendesk.android.ZendeskUser
import zendesk.messaging.android.DefaultMessagingFactory
import zendesk.messaging.android.UserColors
import zendesk.android.events.ZendeskEvent
import zendesk.android.events.ZendeskEventListener
import android.graphics.Color


class ZendeskMessaging(private val plugin: ZendeskMessagingPlugin, private val channel: MethodChannel) {
    companion object {
        const val tag = "[ZendeskMessaging]"

        // Method channel callback keys
        const val initializeSuccess: String = "initialize_success"
        const val initializeFailure: String = "initialize_failure"
        const val loginSuccess: String = "login_success"
        const val loginFailure: String = "login_failure"
        const val logoutSuccess: String = "logout_success"
        const val logoutFailure: String = "logout_failure"
        const val unreadMessages: String = "unread_messages"
    }

    // To create and use the event listener:
    val zendeskEventListener = ZendeskEventListener { zendeskEvent ->
        when (zendeskEvent) {
            is ZendeskEvent.UnreadMessageCountChanged -> {

                channel.invokeMethod(unreadMessages, mapOf("messages_count" to zendeskEvent.currentUnreadCount))

            }
            else -> {
                // Default branch for forward compatibility with Zendesk SDK and its `ZendeskEvent` expansion
            }
        }
    }

    fun initialize(channelKey: String, colors: Map<String, Long>?) {
        println("$tag - Channel Key - $channelKey")

        val userColors = UserColors(
            onPrimary = colors?.get("onPrimary")?.let { Color(it.toInt()) } ?: Color.Black,
            onMessage = colors?.get("onMessage")?.let { Color(it.toInt()) } ?: Color.Black,
            onAction = colors?.get("onAction")?.let { Color(it.toInt()) } ?: Color.Black
        )

        val userColorsDark = UserColors(
            onPrimary = colors?.get("onPrimaryDark")?.let { Color(it.toInt()) } ?: Color.Black,
            onMessage = colors?.get("onMessageDark")?.let { Color(it.toInt()) } ?: Color.Black,
            onAction = colors?.get("onActionDark")?.let { Color(it.toInt()) } ?: Color.Black
        )

        val factory = DefaultMessagingFactory(
            userLightColors = userColors,
            userDarkColors = userColorsDark
        )
        
        Zendesk.initialize(
            plugin.activity!!,
            channelKey,
            successCallback = { value ->
                plugin.isInitialized = true;
                println("$tag - initialize success - $value")
                channel.invokeMethod(initializeSuccess, null)
            },
            failureCallback = { error ->
                plugin.isInitialized = false;
                println("$tag - initialize failure - $error")
                channel.invokeMethod(initializeFailure, mapOf("error" to error.message))
            },
            messagingFactory = factory
        )
    }

    fun invalidate() {
        Zendesk.instance.removeEventListener(zendeskEventListener)
        Zendesk.invalidate()
        plugin.isInitialized = false;
        println("$tag - invalidated")
    }

    fun show() {
        Zendesk.instance.messaging.showMessaging(plugin.activity!!, Intent.FLAG_ACTIVITY_NEW_TASK)
        println("$tag - show")
    }

    fun getUnreadMessageCount(): Int {
        return try {
            Zendesk.instance.messaging.getUnreadMessageCount()
        }catch (error: Throwable){
            0
        }
    }

    fun setConversationTags(tags: List<String>){
        Zendesk.instance.messaging.setConversationTags(tags)
    }

    fun clearConversationTags(){
        Zendesk.instance.messaging.clearConversationTags()
    }

    fun loginUser(jwt: String) {
        Zendesk.instance.loginUser(
            jwt,
            { value: ZendeskUser? ->
                plugin.isLoggedIn = true;
                value?.let {
                    channel.invokeMethod(loginSuccess, mapOf("id" to it.id, "externalId" to it.externalId))
                } ?: run {
                    channel.invokeMethod(loginSuccess, mapOf("id" to null, "externalId" to null))
                }
            },
            { error: Throwable? ->
                println("$tag - Login failure : ${error?.message}")
                println(error)
                channel.invokeMethod(loginFailure, mapOf("error" to error?.message))
            })
    }

    fun logoutUser() {
        GlobalScope.launch (Dispatchers.Main)  {
            try {
                Zendesk.instance.logoutUser(successCallback = {
                    plugin.isLoggedIn = false;
                    channel.invokeMethod(logoutSuccess, null)
                }, failureCallback = {
                    channel.invokeMethod(logoutFailure, null)
                });
                Zendesk.instance.removeEventListener(zendeskEventListener)
            } catch (error: Throwable) {
                println("$tag - Logout failure : ${error.message}")
                channel.invokeMethod(logoutFailure, mapOf("error" to error.message))
            }
        }
    }

    fun listenMessageCountChanged() {
        // To add the event listener to your Zendesk instance:
        Zendesk.instance.addEventListener(zendeskEventListener)
    }

    fun setConversationFields(fields: Map<String, String>){
        Zendesk.instance.messaging.setConversationFields(fields)
    }

    fun clearConversationFields(){
        Zendesk.instance.messaging.clearConversationFields()
    }
}
