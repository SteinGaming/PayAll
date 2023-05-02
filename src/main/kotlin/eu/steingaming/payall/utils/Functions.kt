package eu.steingaming.payall.utils


class Result<T>(private val value: T?, private val error: Throwable?) {
    fun getOrPrintErr(): T? {
        error?.printStackTrace()
        return value
    }
}

fun <T> tryUntilNoErr(vararg inits: () -> T): Result<T> {
    var err: Throwable? = null
    for (init in inits) {
        try {
            return Result(init(), null)
        } catch (e: Throwable) {
            err = e
        }
    }
    return Result(null, err)
}
