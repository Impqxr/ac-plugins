version = "0.0.4"
description = "Search for images in Gelbooru"

aliucord {
    changelog.set("""
        v0.0.4: Fixed some issues with pages, optimized arguments
        v0.0.3: Fixed deprecated warning, error with -1 page number. Added &id for simple reverse search 
        v0.0.2: Encoding tags
        v0.0.1: Initial beta release
    """.trimIndent())

    // Excludes this plugin from the updater, meaning it won't show up for users.
    // Set this if the plugin is unfinished
    // excludeFromUpdaterJson.set(true)
}
