package eu.steingaming.payall

//import com.mojang.logging.LogUtils
import com.google.common.util.concurrent.Uninterruptibles
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import eu.steingaming.payall.utils.find
import eu.steingaming.payall.utils.findField
import eu.steingaming.payall.utils.findFieldType
import eu.steingaming.payall.utils.findFieldTypeWithSuper
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.commands.CommandSourceStack
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*

//import org.slf4j.Logger
//import org.slf4j.LoggerFactory

@Mod(PayAll.MODID)
class PayAll {
    companion object {
        lateinit var instance: PayAll
        const val MODID = "payall"
        //val logger: Logger = try { LogUtils.getLogger() } catch (_: Throwable) {
        //    LoggerFactory.getLogger(PayAll::class.java)
        //}
    }

    private var job: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default)


    private var chatMethod: Method? = null

    val minecraft: Minecraft = Minecraft::class.java.declaredMethods.find { it.returnType == Minecraft::class.java }!!
        .invoke(null) as Minecraft

    private fun sendMessage(str: String) {
        CommandExecutor.tryUntilNoErr(
            {
                Minecraft.getInstance().player?.sendSystemMessage(net.minecraft.network.chat.Component.literal(str))
            },
            {
                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.nullToEmpty(str),
                    false
                )
            },
            { // 1.16.5
                val player = find<Minecraft, Any>(minecraft, findFieldType(Class.forName("net.minecraft.client.entity.player.ClientPlayerEntity")))!!
                (chatMethod ?: player::class.java.declaredMethods.find {
                    it.parameterCount == 2
                            && it.parameters[0].type == Class.forName("net.minecraft.util.text.ITextComponent")
                            && it.parameters[1].type == Boolean::class.java
                }!!.also { chatMethod = it }).invoke(
                    player, Class.forName("net.minecraft.util.text.ITextComponent")
                        .declaredMethods.find { it.parameterCount == 1 && it.parameters[0].type == String::class.java }!!
                        .invoke(
                            null, str
                        ), false
                )

                //ITextComponent.nullToEmpty(str)
            }
        )?.printStackTrace()
    }

    private fun usage() {
        sendMessage("§cUsage: payall <delay seconds> <amount> [custom-command]...")
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

    private fun handle(delay: Double, amount: Long, vararg cmd: String, dryRun: Boolean = false) {
        job?.takeUnless { !it.isActive || it.isCompleted }?.cancel() ?: let {
            //val delay = split.getDoubleOrUsage(1) ?: return@a
            //val amount = split.getLongOrUsage(2) ?: return@a
            val newCmd = cmd.takeUnless { it.isEmpty() }?.joinToString(separator = " ") ?: "pay ! $"
            job = scope.launch {
                run(newCmd, amount, delay, dryRun)
            }
            return
        }
        job = null
        sendMessage("§aStopped PayAll!")
    }

    private val payAllCMD: (String, Boolean) -> LiteralArgumentBuilder<*> = { name, dry ->
        LiteralArgumentBuilder.literal<Any>(name).executes { usage(); return@executes 1 }
            .then(
                RequiredArgumentBuilder.argument<Any, Double>(
                    "delay",
                    DoubleArgumentType.doubleArg()
                ).executes { usage(); return@executes 1 }.then(
                    RequiredArgumentBuilder.argument<Any, Long>(
                        "amount",
                        LongArgumentType.longArg()
                    ).executes {
                        handle(
                            DoubleArgumentType.getDouble(it, "delay"),
                            LongArgumentType.getLong(it, "amount"),
                            cmd = emptyArray(),
                            dryRun = dry
                        )
                        return@executes 1
                    }.then(
                        RequiredArgumentBuilder.argument<Any, String>(
                            "command",
                            StringArgumentType.greedyString()
                        ).executes {
                            handle(
                                DoubleArgumentType.getDouble(it, "delay"),
                                LongArgumentType.getLong(it, "amount"),
                                cmd = StringArgumentType.getString(it, "command").split(" ", "-").toTypedArray(),
                                dryRun = dry
                            )
                            return@executes 1
                        })
                )
            )
    }


    private var _recentChat: MutableList<String>? = null
        get() {
            return field ?: (try {
                Minecraft.getInstance().gui.chat.recentChat
            } catch (_: Throwable) {
                null
            } ?: find( // I hate 1.16.5
                minecraft,
                findFieldType(Class.forName("net.minecraft.client.gui.IngameGui")),
                findFieldTypeWithSuper(Class.forName("net.minecraft.client.gui.NewChatGui")),
                findFieldTypeWithSuper(List::class.java) // has 3 different list, but firs one is the correct one LUCKILY
            )!!).also {
                field = it
            }
        }

    private val recentChat: MutableList<String>
        get() = _recentChat!!


    init {
        instance = this
        try {
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.RegisterClientCommandsEvent>(
                EventPriority.HIGHEST
            ) {
                val init: (String, Boolean) -> Unit = { name, dry ->
                    it.dispatcher.register(
                        payAllCMD(name, dry) as LiteralArgumentBuilder<CommandSourceStack>
                    )
                }
                init("payall", false)
                init("payalldry", true)
            }
        } catch (t: Throwable) {
            MinecraftForge.EVENT_BUS.addListener<ClientChatEvent> e@{ // 1.18.0 is missing the ClientCommandHandler >:(
                val split = it.originalMessage.split(" ") ?: return@e
                if (!split[0].startsWith("/payall")) return@e
                it.isCanceled = true
                if (recentChat.let { r -> r.isEmpty() || r.last() == it.originalMessage })
                    recentChat.add(it.originalMessage)
                val dry = split[0].endsWith("dry")
                val delay = split.getDoubleOrUsage(1) ?: return@e
                val amount = split.getLongOrUsage(2) ?: return@e
                handle(delay, amount, cmd = split.subList(3, split.size).toTypedArray(), dryRun = dry)
            }
        }

        //MinecraftForge.EVENT_BUS.addListener<ClientChatEvent>(EventPriority.HIGHEST) a@{ it ->
        //    // ClientCommandHandler.getDispatcher().setConsumer { context, success, result ->  }
        //    val split = it.originalMessage.split(" ")
        //    if (split.getOrNull(0)?.lowercase()?.startsWith("payall") != true) return@a
        //    if (Minecraft.getInstance().gui.chat.recentChat.last() != it.originalMessage) // Check for versions lower than 1.19, which don't add the message when cancelled
        //        Minecraft.getInstance().gui.chat.addRecentChat(it.originalMessage)
        //    val dryRun = split[0].lowercase().endsWith("dry")
        //    it.isCanceled = true

        //}
    }


    private inline fun <reified T> get(run: () -> T?): T? {
        return try {
            run()
        } catch (t: Throwable) {
            null
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
                *get {
                    Minecraft.getInstance().currentServer?.playerList?.mapNotNull { it?.string }?.toTypedArray()
                } ?: arrayOf(),
                *get {
                    Minecraft.getInstance().player?.connection?.onlinePlayers?.map { it.profile.name }
                        ?.toTypedArray()
                } ?: arrayOf(),
                *get { Minecraft.getInstance().level?.players()?.map { it.gameProfile.name }?.toTypedArray() }
                    ?: arrayOf() // This is only as a fallback, no good else
            ).apply { remove(minecraft.player?.gameProfile?.name) }.toList()
                .shuffled() // we do a bit of gambling :D
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
                CommandExecutor.runCommand(newCMD)
                delay((delay * 1000L).toLong())
            }
            sendMessage("§aDone sending to ${players.size} players!")
            job = null
        }
    }
}