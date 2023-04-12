package eu.steingaming.payall

//import com.mojang.logging.LogUtils
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import eu.steingaming.payall.utils.*
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod
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
                minecraft.player?.sendSystemMessage(net.minecraft.network.chat.Component.literal(str))
            },
            {
                minecraft.player?.displayClientMessage(
                    net.minecraft.network.chat.Component.nullToEmpty(str),
                    false
                )
            },
            { // 1.16.5 AND 1.12.2
                val player = find<Minecraft, Any>(
                    minecraft,
                    findFieldType(Class.forName("net.minecraft.client.entity.player.ClientPlayerEntity"))
                )!!
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

    /**
     * @return if was active
     */
    private fun checkActive(): Boolean {
        job?.takeUnless { !it.isActive || it.isCompleted }?.cancel() ?: return false
        job = null
        sendMessage("§aStopped PayAll!")
        return true
    }

    private fun usage() {
        if (checkActive()) return
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
        if (checkActive()) return
        job = scope.launch {
            run(cmd.takeUnless { it.isEmpty() }?.joinToString(separator = " ") ?: "pay ! $", amount, delay, dryRun)
        }
    }

    private val payAllCMD: (String, Boolean) -> Any? = { name, dry ->
        try {
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
        } catch (e: Throwable) {
            null
        }
    }


    private var _recentChat: MutableList<String>? = null
        get() {
            return field ?: (try {
                minecraft.gui.chat.recentChat
            } catch (_: Throwable) {
                null
            } ?: find<Any, MutableList<String>>( // I hate 1.16.5
                minecraft,
                findFieldType(Class.forName("net.minecraft.client.gui.IngameGui")) or findFieldType("net.minecraft.client.gui.GuiIngame"),
                findFieldType(
                    Class.forName("net.minecraft.client.gui.NewChatGui"),
                    depth = 2
                ) or findFieldType("net.minecraft.client.gui.GuiNewChat"),
                findFieldType(
                    List::class.java,
                    depth = 2
                ) // has 3 different list, but firs one is the correct one LUCKILY
            )!!).also {
                field = it
            }
        }

    private val recentChat: MutableList<String>
        get() = _recentChat!!


    init {
        instance = this
        @Suppress("UNCHECKED_CAST")
        fun messageExec(it: String): Boolean {
            val split = it.split(" ")
            if (!split[0].startsWith("/payall")) return false
            if (recentChat.let { r -> r.isEmpty() || r.last() != it })
                recentChat.add(it)
            val dry = split[0].endsWith("dry")
            val delay = split.getDoubleOrUsage(1) ?: return true
            val amount = split.getLongOrUsage(2) ?: return true
            handle(delay, amount, cmd = split.subList(3, split.size).toTypedArray(), dryRun = dry)
            return true
        }
        CommandExecutor.tryUntilNoErr(
            {
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
            },
            {
                MinecraftForge.EVENT_BUS.addListener<ClientChatEvent> e@{ // 1.18.0 is missing the ClientCommandHandler >:(
                    it.isCanceled = messageExec(it.originalMessage)
                }
            },
            { // 1.12.2
                MinecraftForge.EVENT_BUS.register(object : Any() {
                    fun onChat(e: ClientChatEvent) {
                        e.isCanceled = messageExec(e.originalMessage)
                    }
                }.apply {
                    this::class.java.declaredMethods[0].let {
                        (it::class.java.superclass.getDeclaredField("declaredAnnotations").apply { isAccessible = true }
                            .get(it) as MutableMap<Class<out Annotation>, Annotation>)[Class.forName("net.minecraftforge.fml.common.Mod.EventHandler") as Class<out Annotation>] =
                            Class.forName("net.minecraftforge.fml.common.Mod.EventHandler").getConstructor()
                                .newInstance() as Annotation
                    }
                })
            }
        )
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
                    minecraft.currentServer?.playerList?.mapNotNull { it?.string }?.toTypedArray()
                } ?: get { // 1.16.5 mappings
                    var i = 0
                    var j = 0
                    find<Minecraft, Any>(
                        minecraft,
                        findFieldType("net.minecraft.client.multiplayer.ServerData"),
                        findFieldType(List::class.java, depth = 2) or findFieldType(String::class.java, depth = 2) {
                            j++ == 5
                        }
                    )?.let { v ->
                        when (v) {
                            is List<*> -> v.mapNotNull { // net.minecraft.util.text.ITextComponent non-existent :|
                                find<Any?, String>(v, findMethod({
                                    returnType == String::class.java && i++ == 1
                                }))
                            }

                            is String -> v.split("\n")
                            else -> throw IllegalArgumentException()
                        }
                    }?.toTypedArray()
                } ?: emptyArray(),
                *get {
                    minecraft.player?.connection?.onlinePlayers?.map { it.profile.name }
                        ?.toTypedArray()
                } ?: get {
                    find<Minecraft, Map<UUID, *>>(
                        minecraft,
                        findFieldType("net.minecraft.client.entity.player.ClientPlayerEntity") or findFieldType("net.minecraft.client.entity.EntityPlayerSP"),
                        findFieldType("net.minecraft.client.network.play.ClientPlayNetHandler") or findFieldType("net.minecraft.client.network.NetHandlerPlayClient"),
                        findFieldType(Map::class.java)
                    )?.values?.mapNotNull {
                        find<Any?, String>(
                            it,
                            findFieldType("com.mojang.authlib.GameProfile"),
                            findFieldType(String::class.java)
                        )
                    }?.toTypedArray()
                } ?: emptyArray(),
                *get { minecraft.level?.players()?.map { it.gameProfile.name }?.toTypedArray() }
                    ?: emptyArray() // This is only as a fallback, no good else TODO 1.16.5 + 1.12.2 impl
            ).apply {
                CommandExecutor.tryUntilNoErr(
                    { remove(minecraft.player?.gameProfile?.name) },
                    {
                        remove( // I don't know why I spent 6 hours to prevent paying self, but fun!
                            find<Minecraft, Any>(
                                minecraft,
                                findFieldType("net.minecraft.client.entity.player.ClientPlayerEntity") or findFieldType(
                                    "net.minecraft.client.entity.EntityPlayerSP"
                                ),
                                findMethodByReturnType("net.minecraft.util.text.ITextComponent", depth = 3),
                            ).let {
                                find<Any, String>(
                                    it!!, findFieldType(String::class.java)
                                ).takeUnless { s -> s.isNullOrEmpty() } ?: find<Any, String>(
                                    it,
                                    findFieldType("net.minecraft.util.text.Style", depth = 3),
                                    findFieldType(String::class.java)
                                )
                            }
                        )
                    }
                )?.printStackTrace()
            }.toList()
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