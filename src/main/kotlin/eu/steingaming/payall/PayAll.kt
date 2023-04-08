package eu.steingaming.payall

import com.mojang.logging.LogUtils
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.commands.arguments.ArgumentSignatures
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod
import java.time.Instant

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
                return@a
            }
            job = null
            sendMessage("§aStopped PayAll!")
        }
    }


    private inline fun <reified T> get(run: () -> Array<T>?): Array<T> {
        return try {
            run() ?: arrayOf()
        } catch (t: Throwable) {
            arrayOf()
        }
    }

    enum class Type(val get: (obj: Any, name: String, args: Array<*>) -> Any) {
        FUNCTION({ obj, name, args ->
            obj::class.java.getDeclaredMethod(name, *args.map { (it ?: return@map null)::class.java }.toTypedArray())
                .invoke(obj, *args)
        }),
        FIELD({ obj, name, _ ->
            obj::class.java.getDeclaredField(name).get(obj)
        })
    }

    private fun path(type: Type, name: String, vararg args: Array<*>): Pair<Type, Pair<String, Array<*>>> =
        type to (name to args)


    @Suppress("UNCHECKED_CAST")
    private fun <S, E> from(start: S?, vararg path: Pair<Type, Pair<String, Array<*>>>): E? {
        var current: Any? = start
        for (a in path) {
            try {
                current = a.first.get.invoke(current ?: return null, a.second.first, a.second.second)
            } catch (e: Throwable) {
                return null
            }
        }
        return (current as E?)
    }

    private suspend fun run(
        cmd: String,
        amount: Long,
        delay: Double,
        dryRun: Boolean = false
    ) {
        coroutineScope {
            val players = mutableSetOf<String>( // Only have every player once
                *get {
                    from<ServerData, List<Component?>>(
                        Minecraft.getInstance().currentServer,
                        path(Type.FIELD, "playerList")
                    )?.mapNotNull { it?.string }?.toTypedArray()
                },
                *get {
                    from<ClientPacketListener, Collection<PlayerInfo>>(
                        Minecraft.getInstance().player?.connection,
                        path(Type.FUNCTION, "getListedOnlinePlayers")
                    )?.map { it.profile.name }
                        ?.toTypedArray()
                },
                *get { Minecraft.getInstance().level?.players()?.map { it.gameProfile.name }?.toTypedArray() }
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
                val newCMD = cmd.replace("!", p).replace("$", amount.toString())
                try {
                    Minecraft.getInstance().connection!!::class.java.getDeclaredMethod(
                        "m_246623_", // Obfuscated "sendCommand"
                        String::class.java
                    ).invoke(
                        Minecraft.getInstance().connection!!, newCMD
                    )
                } catch (e: Throwable) {
                    e.printStackTrace()
                    Minecraft.getInstance().player?.connection?.send( // For 1.19.0
                        ServerboundChatCommandPacket::class.java.getConstructor(
                            String::class.java,
                            Instant::class.java,
                            ArgumentSignatures::class.java,
                            Boolean::class.java
                        ).newInstance(newCMD, Instant.now(), ArgumentSignatures::class.java.getConstructor(Long::class.java, java.util.Map::class.java).newInstance(0L, java.util.Map.of<String, ByteArray>()), true)
                    )
                }
                delay((delay * 1000L).toLong())
            }
            sendMessage("§aDone sending to ${players.size} players!")
            job = null
        }
    }
}