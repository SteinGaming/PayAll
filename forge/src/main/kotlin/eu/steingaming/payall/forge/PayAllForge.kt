package eu.steingaming.payall.forge

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import eu.steingaming.payall.PayAll
import eu.steingaming.payall.forge.gui.PayAllMenu
import eu.steingaming.payall.utils.*
import kotlinx.coroutines.*
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ArgumentSignatures
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.client.settings.KeyModifier
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod
import java.lang.reflect.Method
import java.time.Instant
import java.util.*


@Mod(PayAll.MODID)
class PayAllForge : PayAll() {
    companion object {
        lateinit var instance: PayAllForge
    }

    private var chatMethod: Method? = null

    private val keyBind by lazy {
        KeyMapping(
            "key.payall.open",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_ADD,
            "key.categories.misc"
        )
    }
    val minecraft: Minecraft = Minecraft::class.java.declaredMethods.find { it.returnType == Minecraft::class.java }!!
        .invoke(null) as Minecraft

    init {
        instance = this

        tryUntilNoErr({
            MinecraftForge.EVENT_BUS.addListener<RegisterKeyMappingsEvent> {
                it.register(keyBind)
            }
        }).getOrPrintErr()

        @Suppress("UNCHECKED_CAST")
        tryUntilNoErr({
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
        }, {
            MinecraftForge.EVENT_BUS.addListener<ClientChatEvent> e@{ // 1.18.0 is missing the ClientCommandHandler >:(
                val split = it.originalMessage.split(" ")
                if (!split[0].startsWith("payalldry")) return@e
                it.isCanceled = true
                if (recentChat.let { r -> r.isEmpty() || r.last() != it.originalMessage })
                    recentChat.add(it.originalMessage)
                val dry = split[0].endsWith("dry")
                val delay = split.getDoubleOrUsage(1) ?: return@e
                val amount = split.getLongOrUsage(2) ?: return@e
                handle(delay, amount, cmd = split.subList(3, split.size).toTypedArray(), dryRun = dry)
            }
        }).getOrPrintErr()
        tryUntilNoErr<Unit>(
            {
                MinecraftForge.EVENT_BUS.addListener<TickEvent.ClientTickEvent> {
                    if (it.phase == TickEvent.Phase.END)
                        while (keyBind.consumeClick()) {
                            minecraft.pushGuiLayer(PayAllMenu())
                        }
                }
            } // TODO Add previous version support
        ).getOrPrintErr()
    }

    override fun sendMessage(str: String) {
        tryUntilNoErr<Unit>(
            {
                Minecraft.getInstance().player?.sendSystemMessage(Component.literal(str))
            },
            {
                Minecraft.getInstance().player?.displayClientMessage(
                    Component.nullToEmpty(str),
                    false
                )
            },
            { // 1.16.5
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
            }
        ).getOrPrintErr()
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
                findFieldType(Class.forName("net.minecraft.client.gui.NewChatGui"), depth = 2),
                findFieldType(
                    List::class.java,
                    depth = 2
                ) // has 3 different list, but first one is the correct one LUCKILY
            )!!).also {
                field = it
            }
        }


    private val recentChat: MutableList<String>
        get() = _recentChat!!


    override fun getOwnName(): String? {
        return tryUntilNoErr({ minecraft.player?.gameProfile?.name },
            {
                // I don't know why I spent 6 hours to prevent paying self, but fun!
                find<Minecraft, Any>(
                    minecraft,
                    findFieldType("net.minecraft.client.entity.player.ClientPlayerEntity"),
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

            }).getOrPrintErr()
    }

    override fun getPlayers(): MutableSet<String> = mutableSetOf( // Only have every player once
        *get {
            Minecraft.getInstance().currentServer?.playerList?.mapNotNull { it?.string }?.toTypedArray()
        } ?: get { // 1.16.5 mappings
            var i = 0
            find<Minecraft, List<*>>(
                minecraft,
                findFieldType("net.minecraft.client.multiplayer.ServerData"),
                findFieldType(List::class.java, depth = 2)
            )?.mapNotNull { // net.minecraft.util.text.ITextComponent non-existent :|
                find<Any?, String>(it, findMethod({
                    returnType == String::class.java && i++ == 1
                }))
            }?.toTypedArray()
        } ?: emptyArray(),
        *get {
            Minecraft.getInstance().player?.connection?.onlinePlayers?.map { it.profile.name }
                ?.toTypedArray()
        } ?: get {
            find<Minecraft, Map<UUID, *>>(
                minecraft,
                findFieldType("net.minecraft.client.entity.player.ClientPlayerEntity"),
                findFieldType("net.minecraft.client.network.play.ClientPlayNetHandler"),
                findFieldType(Map::class.java)
            )?.values?.mapNotNull {
                find<Any?, String>(
                    it,
                    findFieldType("com.mojang.authlib.GameProfile"),
                    findFieldType(String::class.java)
                )
            }?.toTypedArray()
        } ?: emptyArray(),
        *get { Minecraft.getInstance().level?.players()?.map { it.gameProfile.name }?.toTypedArray() }
            ?: emptyArray() // This is only as a fallback, no good else TODO 1.16.5 impl
    )


    override fun runCommand(cmd: String) {
        tryUntilNoErr<Unit>({
            Minecraft.getInstance().connection!!::class.java.getDeclaredMethod(
                "m_246623_", // Obfuscated "sendCommand"
                String::class.java
            ).invoke(
                Minecraft.getInstance().connection!!, cmd
            )
        }, {
            Minecraft.getInstance().player?.connection?.send(
                ServerboundChatCommandPacket::class.java.getConstructor( // For 1.19.0
                    String::class.java,
                    Instant::class.java,
                    ArgumentSignatures::class.java,
                    Boolean::class.java
                ).newInstance(
                    cmd,
                    Instant.now(),
                    ArgumentSignatures::class.java.getConstructor(
                        Long::class.java,
                        java.util.Map::class.java
                    ).newInstance(0L, java.util.Map.of<String, ByteArray>()),
                    true
                )

            )

        }, {
            Minecraft.getInstance().player?.connection?.send(
                ServerboundChatCommandPacket::class.java.getConstructor( // For 1.19.2 (maybe 1.19.1)
                    String::class.java,
                    Instant::class.java,
                    Long::class.java,
                    ArgumentSignatures::class.java,
                    Boolean::class.java,
                    net.minecraft.network.chat.LastSeenMessages.Update::class.java
                ).newInstance(
                    cmd,
                    Instant.now(),
                    0L,
                    ArgumentSignatures.EMPTY,
                    true,
                    net.minecraft.network.chat.LastSeenMessages.Update::class.java.getConstructor(
                        net.minecraft.network.chat.LastSeenMessages::class.java,
                        Optional::class.java
                    ).newInstance(
                        net.minecraft.network.chat.LastSeenMessages(listOf()),
                        Optional.empty<Any>()
                    )
                )
            )
        }, { // 1.18.x
            Minecraft.getInstance().player?.connection?.send(
                ServerboundChatPacket::class.java.getConstructor(String::class.java).newInstance("/$cmd")
            )
        }, { // 1.16.5
            val connection = find<Minecraft, Any>(
                minecraft,
                findFieldType(Class.forName("net.minecraft.client.entity.player.ClientPlayerEntity")),
                findFieldType(Class.forName("net.minecraft.client.network.play.ClientPlayNetHandler"), depth = 2),
            )!!

            connection::class.java.declaredMethods.find {
                it.parameterCount == 1 && it.parameters[0].type == Class.forName("net.minecraft.network.IPacket")
            }!!.invoke(
                connection,
                Class.forName("net.minecraft.network.play.client.CChatMessagePacket").getConstructor(String::class.java)
                    .newInstance("/$cmd")
            )
        }).getOrPrintErr()
    }

    override fun isConnected(): Boolean =
        minecraft.currentServer != null
}