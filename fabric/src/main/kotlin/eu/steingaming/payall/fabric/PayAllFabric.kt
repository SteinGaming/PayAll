package eu.steingaming.payall.fabric

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import eu.steingaming.payall.PayAll
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

class PayAllFabric : ModInitializer, PayAll() {
    private val payAllCMD: (String, Boolean) -> LiteralArgumentBuilder<FabricClientCommandSource> = { name, dry ->
        LiteralArgumentBuilder.literal<FabricClientCommandSource>(name).executes { usage(); return@executes 1 }
            .then(
                RequiredArgumentBuilder.argument<FabricClientCommandSource, Double>(
                    "delay",
                    DoubleArgumentType.doubleArg()
                ).executes { usage(); return@executes 1 }.then(
                    RequiredArgumentBuilder.argument<FabricClientCommandSource, Long>(
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
                        RequiredArgumentBuilder.argument<FabricClientCommandSource, String>(
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

    override fun onInitialize() {
        println("INITIALIZING PAYALL!")
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, registryAccess ->
            println("EXECUTED")
            dispatcher?.register(
                payAllCMD("payall", false)
            )
            dispatcher?.register(
                payAllCMD("payalldry", true)
            )
        })
    }

    override fun sendMessage(str: String) {
        MinecraftClient.getInstance().player?.sendMessage(Text.of(str))
    }

    override fun getOwnName(): String? =
        MinecraftClient.getInstance().player?.gameProfile?.name


    override fun getPlayers(): MutableSet<String> {
        return mutableSetOf(
            *(MinecraftClient.getInstance().currentServerEntry?.players?.sample?.map {
                it.name
            }?.toTypedArray() ?: arrayOf()),
            *(MinecraftClient.getInstance().networkHandler?.playerList?.map { it.profile.name }?.toTypedArray()
                ?: arrayOf())
        )
    }

    override fun runCommand(cmd: String) {
        MinecraftClient.getInstance().networkHandler?.sendCommand(cmd)
    }

    override fun isConnected(): Boolean =
        MinecraftClient.getInstance().networkHandler?.serverInfo != null
}