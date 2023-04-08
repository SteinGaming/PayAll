package eu.steingaming.payall.minestom

import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandManager
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.fakeplayer.FakePlayer
import net.minestom.server.entity.fakeplayer.FakePlayerOption
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.block.Block
import java.util.*


inline fun CommandManager.registerNew(name: String, crossinline init: Command.() -> Unit) {
    register(Command(name).apply(init))
}

fun main(args: Array<String>) {
    val server = MinecraftServer.init()
    var reverse = false // To test custom command :)
    fun registerPay() = MinecraftServer.getCommandManager().apply {
        let {
            unregister(getCommand("pay") ?: return@let)
        }
        registerNew("pay") {
            val player = ArgumentType.String("player")
            val amount = ArgumentType.Long("amount")
            addSyntax({ sender, context ->
                if (sender !is Player) return@addSyntax
                MinecraftServer.LOGGER.info("${sender.username} paid ${context.get(amount)} to ${context.get(player)}!")
            }, *arrayOf(player, amount).apply { if (reverse) reverse() })
        }
        //registerNew("payall") {
        //    setDefaultExecutor { sender, context ->
        //        if (sender is Player) sender.kick("LLLLLLLLLLLLLL")
        //    }
        //}
    }
    registerPay()
    MinecraftServer.getCommandManager().apply {
        registerNew("reverse") {
            setDefaultExecutor { sender, context ->
                reverse = !reverse
                registerPay()
                sender.sendMessage("Done!")
            }
        }
        registerNew("deez") {
            setDefaultExecutor { sender, context ->
                for (i in 0..20) {
                    FakePlayer.initPlayer(UUID.randomUUID(), "Fake-$i", FakePlayerOption().setRegistered(true).setInTabList(true)) {
                        it.updateNewViewer(sender as Player)
                    }
                }
            }
        }
    }
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer().apply {
        setGenerator {
            it.modifier().fillHeight(0, 1, Block.NETHERITE_BLOCK)
        }
    }
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { e ->
        e.setSpawningInstance(instance)
        e.player.respawnPoint = Pos(0.0, 2.0, 0.0)
    }
    server.start("localhost", 25565)

}