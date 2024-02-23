package com.github.impqxr.acplugins

import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.Logger
import com.aliucord.Http.simpleGet
import com.aliucord.Utils.pluralise
import com.discord.api.commands.ApplicationCommandType
import android.content.Context
import com.aliucord.utils.GsonUtils.fromJson
import com.discord.api.message.embed.MessageEmbed
//import com.google.gson.annotations.SerializedName
import java.net.URLEncoder
import com.google.gson.Gson
import kotlin.math.max
import kotlin.math.min
import kotlin.math.truncate
import kotlin.random.Random

val booruLogger = Logger("Booru")
const val MAX_PAGE = 476 // Too deep! Pull it back some. Holy fuck.
const val MAX_PAGE_POSTS = 42
const val URL = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=$MAX_PAGE_POSTS"

data class BooruResponse(
    val attributes: BooruAttributes,
    val post: List<BooruPost>
)
data class BooruRequest(
    val post: List<BooruPost>?,
    val count: Int
)

data class BooruAttributes(
    val count: Int
)

data class BooruPost(
    val fileUrl: String
)

fun invalidArgumentEmbed(message: String): MessageEmbed {
    return MessageEmbedBuilder()
            .setTitle("Invalid `$message` argument!")
            .setDescription("Please read how to use it, thanks.")
            .setColor(0x00FF0000)
            .build()
}

fun booruRequest(tags: String, page: Int): BooruRequest {
    var resp = if (tags == "") {
        simpleGet("$URL&pid=$page")
    } else {
        simpleGet("$URL&pid=$page&tags=${URLEncoder.encode(tags)}")
    }

    // fuck you SerializedName(), you broke my heart :(
    resp = resp.replace("\"@attributes\"", "\"attributes\"")
    resp = resp.replace("\"file_url\"", "\"fileUrl\"")

    val respJson = Gson().fromJson(resp, BooruResponse::class.java)

    return BooruRequest(respJson.post, respJson.attributes.count)
}

@SuppressWarnings("unused")
@AliucordPlugin(requiresRestart = false)
class Booru : Plugin() {
    override fun start(context: Context) {
        commands.registerCommand("booru", "Search for images from Gelbooru", listOf(
            Utils.createCommandOption(ApplicationCommandType.STRING, "tags", "Filtering by tags (space is a separator between tags)"),
            Utils.createCommandOption(ApplicationCommandType.STRING, "rating", "Rating of images",
                choices = listOf(
                    Utils.createCommandChoice("Explicit", "explicit"),
                    Utils.createCommandChoice("Questionable", "questionable"),
                    Utils.createCommandChoice("Sensitive", "sensitive"),
                    Utils.createCommandChoice("General", "general"))
            ),
            Utils.createCommandOption(ApplicationCommandType.INTEGER, "count", "How many images to send in one message (1-5)"),
            Utils.createCommandOption(ApplicationCommandType.INTEGER, "page", "Page (Max limit $MAX_PAGE)"),
            Utils.createCommandOption(ApplicationCommandType.INTEGER, "postNumber", "Post number (Up to $MAX_PAGE_POSTS per 1 page)"),
        )) { ctx ->
            try {
                var tags = ctx.getStringOrDefault("tags", "").trim()
                val rating = ctx.getStringOrDefault("rating", "")
                val count = ctx.getIntOrDefault("count", 1) - 1
                val pageArg = ctx.getIntOrDefault("page", -1)
                val postNumber = ctx.getIntOrDefault("postNumber", -1)

                if (rating != "") {
                    tags += " rating:$rating"
                }
                // TODO: improve it somehow
                if (count > 4 || count < 0) {
                    return@registerCommand CommandsAPI.CommandResult("",
                        listOf(
                            invalidArgumentEmbed("count")
                        ),
                        false
                    )
                }
                if (pageArg > MAX_PAGE) {
                    return@registerCommand CommandsAPI.CommandResult("",
                        listOf(
                            invalidArgumentEmbed("page")
                        ),
                        false
                    )
                }
                if (postNumber > MAX_PAGE_POSTS) {
                    return@registerCommand CommandsAPI.CommandResult("",
                        listOf(
                            invalidArgumentEmbed("post number")
                        ),
                        false
                    )
                }
                booruLogger.debug("Tags and Rating: $tags\n" +
                        "Count: ${count + 1}\n" +
                        "Page Argument: $pageArg\n" +
                        "Post Number: $postNumber")

                var page: Int

                if (pageArg < 0) {
                    val responseInfo = booruRequest(tags, 0)

                    if (responseInfo.count == 0 || responseInfo.post == null) {
                        return@registerCommand CommandsAPI.CommandResult(
                            "",
                            listOf(
                                MessageEmbedBuilder()
                                    .setTitle("No results found!")
                                    .setColor(0x00FFFF00)
                                    .build()
                            ),
                            false
                        )
                    } else {
                        booruLogger.debug("Pages: ${responseInfo.count / MAX_PAGE_POSTS}\nPosts count: ${responseInfo.count}")
                        page = truncate((responseInfo.count / MAX_PAGE_POSTS).toDouble()).toInt()
                        page = Random.nextInt(0, min(page, MAX_PAGE))
                        booruLogger.debug("Selected page: $page")
                    }
                } else {
                    page = pageArg
                }

                val response = booruRequest(tags, page)
                if (response.count == 0 || response.post == null) {
                    val pages = truncate((response.count / MAX_PAGE_POSTS).toDouble()).toInt()
                    val notFoundEmbed = MessageEmbedBuilder()
                        .setTitle("No results found!")
                        .setColor(0x000000FF)
                    if (pages > 0) {
                        notFoundEmbed.setDescription("Tag contains only ${pluralise(pages, "page")}")
                    }
                    return@registerCommand CommandsAPI.CommandResult("",
                        listOf(
                            notFoundEmbed.build()
                        ),
                        false
                    )
                }

                val posts: MutableList<String> = ArrayList()
                val randomPosts: List<BooruPost> = if (response.post.count() <= count) {
                    booruLogger.debug("The whole has been selected (${response.post.count()})")
                    response.post
                } else {
                    if (postNumber < 0) {
                        val randomPostNumber = max(Random.nextInt(0, response.post.count() - count), 0)
                        booruLogger.debug("Random Post Number: $randomPostNumber - ${randomPostNumber + count}\n" +
                                "Posts in the page: ${response.post.count()}")
                        response.post.slice(randomPostNumber - 1 until randomPostNumber+count)
                    } else {
                        val nonRandomPostNumber = min(postNumber, response.post.count())
                        booruLogger.debug("Non Random Post Number: ${max(nonRandomPostNumber - count, 0)} - ${nonRandomPostNumber}\n" +
                                "Posts in the page: ${response.post.count()}")
                        response.post.slice(max(nonRandomPostNumber - count, 0) - 1 until nonRandomPostNumber)
                    }
                }

                for (post in randomPosts) {
                    posts.add(post.fileUrl)
                }

                CommandsAPI.CommandResult(posts.joinToString("\n"),
                    emptyList(),
                    true
                )

            } catch (e: Exception) {
                booruLogger.debug(e.toString())
                booruLogger.errorToast(e.cause)
                CommandsAPI.CommandResult(
                    "",
                    listOf(
                        MessageEmbedBuilder()
                            .setTitle("Something went wrong. Try again later.")
                            .setColor(0x00FF0000)
                            .build()
                    ),
                    false
                )
            }
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
