package eu.steingaming.payall

import kotlinx.coroutines.*


abstract class PayAll {
    companion object {
        //lateinit var instance: PayAll
        const val MODID = "payall"
    }

    private var job: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    abstract fun sendMessage(str: String)

    /**
     * @return if was active
     */
    private fun checkActive(): Boolean {
        job?.takeUnless { !it.isActive || it.isCompleted }?.cancel() ?: return false
        job = null
        sendMessage("§aStopped PayAll!")
        return true
    }

    protected fun usage() {
        if (checkActive()) return
        sendMessage("§cUsage: payall <delay seconds> <amount> [custom-command]...")
        sendMessage("§cThe custom command (if given) has to contain \"$\" (replaced by amount) and \"!\" (replaced by player name)")
    }

    protected fun List<String>.getLongOrUsage(index: Int): Long? = getOrNull(index)?.toLongOrNull() ?: run {
        usage()
        null
    }

    protected fun List<String>.getDoubleOrUsage(index: Int): Double? = getOrNull(index)?.toDoubleOrNull() ?: run {
        usage()
        null
    }

    fun handle(delay: Double, amount: Long, vararg cmd: String, dryRun: Boolean = false) {
        if (checkActive()) return
        job = scope.launch {
            run(cmd.joinToString(separator = " ").trim().takeUnless { it.isEmpty() } ?: "pay ! $", amount, delay, dryRun)
        }
    }
    protected inline fun <reified T> get(run: () -> T?): T? {
        return try {
            run()
        } catch (t: Throwable) {
            null
        }
    }


    abstract fun getOwnName(): String?

    abstract fun getPlayers(): MutableSet<String>


    suspend fun run(
        cmd: String,
        amount: Long,
        delay: Double,
        dryRun: Boolean = false
    ) {
        coroutineScope {
            val players = getPlayers().apply {
                remove(getOwnName() ?: return@apply)
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
                if (!isConnected()) {
                    sendMessage("§cInterrupted due to disconnect!")
                    return@coroutineScope
                }
                val p = players.getOrNull(i++) ?: break
                sendMessage("§7Sending: /${cmd.replace("!", p).replace("$", amount.toString())}")
                val newCMD = cmd.replace("!", p).replace("$", amount.toString())
                runCommand(newCMD)
                delay((delay * 1000L).toLong())
            }
            sendMessage("§aDone sending to ${players.size} players!")
            job = null
        }
    }

    abstract fun isConnected(): Boolean

    abstract fun runCommand(cmd: String)
}