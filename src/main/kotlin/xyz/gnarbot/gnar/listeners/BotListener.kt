package xyz.gnarbot.gnar.listeners

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.*
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import xyz.gnarbot.gnar.Bot
import xyz.gnarbot.gnar.commands.Context
import java.awt.Color
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class BotListener(private val bot: Bot) : ListenerAdapter() {
    override fun onGuildJoin(event: GuildJoinEvent) {
        //Don't fire this if the SelfMember joined a longish time ago. This avoids discord fuckups.
        if (event.guild.selfMember.timeJoined.isBefore(OffsetDateTime.now().minusSeconds(30))) return

        //Greet message start.
        val embedBuilder = EmbedBuilder()
                .setThumbnail(event.jda.selfUser.effectiveAvatarUrl)
                .setColor(Color.BLUE)
                .setDescription("Welcome to Octave! The highest quality Discord music bot!\n" +
                        "Please check the links below to get help, and use `_help` to get started!")
                .addField("Important Links",
                        "[Support Server](https://discord.gg/musicbot)\n" +
                                "[Website](https://octave.gg)\n" +
                                "[Invite Link](https://invite.octave.gg)\n" +
                                "[Patreon](https://patreon.com/octave)", true)
                .setFooter("Thanks for using Octave!")


        //Find the first channel we can talk to.
        val channel = event.guild.textChannels.firstOrNull(TextChannel::canTalk)
            ?: return

        channel.sendMessage(embedBuilder.build()).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }

        bot.datadog.gauge("octave_bot.guilds", bot.shardManager.guildCache.size())
        bot.datadog.gauge("octave_bot.users", bot.shardManager.userCache.size())
        bot.datadog.gauge("octave_bot.players", bot.players.size().toLong())
        bot.datadog.incrementCounter("octave_bot.guildJoin")
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        if (event.author.isBot) {
            if (event.author == event.jda.selfUser && bot.options.ofGuild(event.guild).command.isAutoDelete) {
                event.message.delete().queueAfter(10, TimeUnit.SECONDS)
            }
            return
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (bot.isLoaded) {
            val options = bot.options.ofGuild(event.guild)

            // Autorole
            if (options.roles.autoRole != null) {
                val role = event.guild.getRoleById(options.roles.autoRole!!)

                // If role is null then unset
                if (role == null) {
                    options.roles.autoRole = null
                    return
                }

                // If bot cant interact with role then unset
                if (!event.guild.selfMember.canInteract(role) || !event.guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
                    options.roles.autoRole = null
                    return
                }

                // Add the role to the member
                event.guild.addRoleToMember(event.member, role).reason("Auto-role").queue()
            }
        }
    }

    override fun onStatusChange(event: StatusChangeEvent) {
        Bot.getLogger().info(String.format("Shard #%d: Changed from %s to %s", event.jda.shardInfo.shardId, event.oldStatus, event.newStatus))
    }


    override fun onGuildLeave(event: GuildLeaveEvent) {
        bot.players.destroy(event.guild)
        bot.datadog.gauge("octave_bot.guilds", bot.shardManager.guildCache.size())
        bot.datadog.gauge("octave_bot.users", bot.shardManager.userCache.size())
        bot.datadog.gauge("octave_bot.players", bot.players.size().toLong())
        bot.datadog.incrementCounter("octave_bot.guildLeave")
    }

    override fun onReady(event: ReadyEvent) {
        Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " is ready.")
        bot.datadog.incrementCounter("octave_bot.shardReady")
    }

    override fun onResume(event: ResumedEvent) {
        bot.datadog.incrementCounter("octave_bot.shardResume")
        Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " has resumed.")
    }

    override fun onReconnect(event: ReconnectedEvent) {
        bot.datadog.incrementCounter("octave_bot.shardReconnect")
        Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " has reconnected.")
    }

    override fun onDisconnect(event: DisconnectEvent) {
        bot.datadog.incrementCounter("octave_bot.shardDisconnect")
        if (event.isClosedByServer) {
            Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " has disconnected (closed by server). "
                    + "Code: " + event.serviceCloseFrame?.closeCode + " " + event.closeCode)
        } else {
            if (event.clientCloseFrame != null) {
                Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " has disconnected. "
                        + "Code: " + event.clientCloseFrame?.closeCode + " " + event.clientCloseFrame?.closeReason)
            } else {
                Bot.getLogger().info("JDA " + event.jda.shardInfo.shardId + " has disconnected. CCF Null. Code: " + event.closeCode)
            }
        }
    }

    override fun onException(event: ExceptionEvent) {
        bot.datadog.incrementCounter("octave_bot.exception")
        if (!event.isLogged) Bot.getLogger().error("Error thrown by JDA.", event.cause)
    }
}
