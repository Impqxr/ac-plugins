package com.github.impqxr.acplugins

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.RxUtils.await
import com.discord.models.domain.NonceGenerator
import com.discord.restapi.RestAPIParams
import com.discord.utilities.rest.RestAPI
import com.discord.stores.StoreStream
import com.discord.utilities.time.ClockFactory
import com.discord.widgets.chat.MessageContent
import com.discord.widgets.chat.MessageManager
import com.discord.widgets.chat.input.ChatInputViewModel

const val URL = "https://tenor.com/view/sex-gif-14550948129943015206"
const val BRUTAL_URL = "https://tenor.com/view/honkai-honkai-star-rail-sex-sex-alarm-brutal-sex-alarm-gif-17568096709972150607"

fun sendRestMessage(message: String) {
    val msg = RestAPIParams.Message(
        message,
        NonceGenerator.computeNonce(ClockFactory.get()).toString(),
        null,
        null,
        emptyList(),
        null,
        RestAPIParams.Message.AllowedMentions(
            emptyList(),
            emptyList(),
            emptyList(),
            false
        ),
        null,
        null
    )
    Utils.threadPool.execute { Thread.sleep(500)
        RestAPI.api.sendMessage(StoreStream.getChannelsSelected().id, msg).await()
    }
}
@SuppressWarnings("unused")
@AliucordPlugin(requiresRestart = false)
class SexAlarm : Plugin() {

    private val charactersRegex = Regex("\\W+").toPattern()

    override fun start(context: Context) {
        patcher.after<ChatInputViewModel>(
            "sendMessage",
            Context::class.java,
            MessageManager::class.java,
            MessageContent::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType!!,
            Function1::class.java
        ) {
            val messageContent = it.args[2] as MessageContent
            val text = messageContent.textContent

            val sex = charactersRegex.split(text.lowercase()).filter { match -> match.isNotEmpty() }
            val occurrences = sex.count { occurrence -> occurrence == "sex" }
            if ("sex" in sex && text != URL && text != BRUTAL_URL) {
                if (occurrences >= 3) {
                    sendRestMessage(BRUTAL_URL)
                } else {
                    sendRestMessage(URL)
                }
            }

        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
