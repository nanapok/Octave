package xyz.gnarbot.gnar.commands.music

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.music.settings.RepeatOption

@Command(
        aliases = ["repeat", "loop"],
        usage = "(song, playlist, none)",
        description = "Set if the music player should repeat."
)
@BotInfo(
        id = 70,
        category = Category.MUSIC,
        scope = Scope.VOICE
)
class RepeatCommand : MusicCommandExecutor(true, false, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            context.send().error("Valid options are `${RepeatOption.values().joinToString()}`").queue()
            return
        }

        val option = try {
            RepeatOption.valueOf(args[0].toUpperCase())
        } catch (e: IllegalArgumentException) {
            context.send().error("Valid options are `${RepeatOption.values().joinToString()}`").queue()
            return
        }

        manager.scheduler.repeatOption = option

        context.send().info(
                when (manager.scheduler.repeatOption) {
                    RepeatOption.QUEUE -> "\uD83D\uDD01"
                    RepeatOption.SONG -> "\uD83D\uDD02"
                    RepeatOption.NONE -> "\u274C"
                } + " Music player was set to __**${manager.scheduler.repeatOption}**__."
        ).queue()
    }
}
