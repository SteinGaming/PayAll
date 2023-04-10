package eu.steingaming.payall

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundChatPacket
import java.time.Instant
import java.util.*
import net.minecraft.commands.arguments.ArgumentSignatures
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket

object CommandExecutor {
    private fun tryUntilNoErr(vararg inits: () -> Unit) {
        for (init in inits) {
            try {
                init()
                break
            } catch (_: Throwable) {
            }
        }
    }
    fun runCommand(cmd: String) {
        tryUntilNoErr({
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
        }, { // 1.18.2 and 1.18.1 support
            Minecraft.getInstance().player?.connection?.send(ServerboundChatPacket::class.java.getConstructor(String::class.java).newInstance("/$cmd"))
        })
    }
}