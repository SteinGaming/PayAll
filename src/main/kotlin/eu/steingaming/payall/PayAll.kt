package eu.steingaming.payall

import com.mojang.logging.LogUtils
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod

@Mod(PayAll.MODID)
class PayAll {
    companion object {
        const val MODID = "payall"
        val logger = LogUtils.getLogger()
    }

    private var job: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun sendMessage(str: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(str))
    }

    private fun usage() {
        sendMessage("§cUsage: /payall <delay seconds> <amount> [custom-command]...")
        sendMessage("§cThe custom command (if given) has to contain \"$\" (replaced by amount) and \"!\" (replaced by player name)")
    }

    private fun List<String>.getLongOrUsage(index: Int): Long? = getOrNull(index)?.toLongOrNull() ?: run {
        usage()
        null
    }

    private fun List<String>.getDoubleOrUsage(index: Int): Double? = getOrNull(index)?.toDoubleOrNull() ?: run {
        usage()
        null
    }

    init {

        MinecraftForge.EVENT_BUS.addListener<ClientChatEvent>(EventPriority.HIGHEST) a@{ it ->
            logger.info("CONTENT: " + it.originalMessage)
            val split = it.originalMessage.split(" ")
            if (split.getOrNull(0)?.lowercase()?.startsWith("payall") != true) return@a
            sendMessage("")
            val dryRun = split[0].lowercase().endsWith("dry")
            it.isCanceled = true
            job?.takeUnless { !it.isActive || it.isCompleted }?.cancel() ?: let {
                val delay = split.getDoubleOrUsage(1) ?: return@a
                val amount = split.getLongOrUsage(2) ?: return@a
                val cmd = split.subList(3, split.size).takeIf { cmd -> cmd.isNotEmpty() }
                    ?.joinToString(separator = " ") ?: "pay ! $"
                job = scope.launch {
                    run(cmd, amount, delay, dryRun)
                }
                sendMessage("")
                return@a
            }
            job = null
            sendMessage("§aStopped PayAll!")
            sendMessage("")
        }
    }

    private suspend fun run(
        cmd: String,
        amount: Long,
        delay: Double,
        dryRun: Boolean = false
    ) {
        coroutineScope {
            val players = mutableSetOf<String>( // Only have every player once
                *Minecraft.getInstance().currentServer?.playerList!!.map { it.string }.toTypedArray(),
                *(Minecraft.getInstance().player?.connection?.listedOnlinePlayers?.map { it.profile.name }
                    ?.toTypedArray() ?: arrayOf()),
                *(Minecraft.getInstance().level?.players()?.map { it.gameProfile.name }?.toTypedArray() ?: arrayOf())
            ).apply { remove(Minecraft.getInstance().player?.gameProfile?.name) }.toList()
            if (dryRun) {
                sendMessage("§aPlayers Indexed: $players")
                sendMessage("§aCMD Example: §6${cmd.replace("!", "PLAYER-NAME").replace("$", amount.toString())} ")
                return@coroutineScope
            }
            if (players.isEmpty()) {
                sendMessage("§cNo Player found! Either no one is online, or the server hides them in a specific way.")
                sendMessage("§cOpen an issue or contact me: https://github.com/SteinGaming/PayAll/issues OR SteinGaming#6292 on Discord")
                return@coroutineScope
            }
            var i = 0
            while (true) {
                ensureActive()
                val p = players.getOrNull(i++) ?: break
                sendMessage("§7Sending: /${cmd.replace("!", p).replace("$", amount.toString())}")
                Minecraft.getInstance().player?.connection?.sendCommand(
                    cmd.replace("!", p).replace("$", amount.toString())
                )
                delay((delay * 1000L).toLong())
            }
            sendMessage("§aDone sending to ${players.size} players!")
            job = null
        }
    }
}