package eu.steingaming.payall.utils

import java.lang.reflect.Field
import java.lang.reflect.Method

class Finder(
    val type: Type,
    val invoker: Any.(previous: Any) -> Boolean,
    val args: Array<out Any?>,
    val superDepth: Int = 0
) {
    enum class Type { FIELD, METHOD }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Finder

        if (type != other.type) return false
        if (invoker != other.invoker) return false
        if (!args.contentEquals(other.args)) return false
        return superDepth == other.superDepth
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + invoker.hashCode()
        result = 31 * result + args.contentHashCode()
        result = 31 * result + superDepth.hashCode()
        return result
    }

}

fun findField(depth: Int = 0, invoker: Field.(Any) -> Boolean): Finder {
    return Finder(Finder.Type.FIELD, invoker as Any.(Any) -> Boolean, emptyArray(), depth)
}

fun findFieldType(clazz: Class<*>, depth: Int = 0, extra: Field.(Any) -> Boolean = { true }): Finder =
    findField(depth) {
        type == clazz && extra(this, it)
    }

fun findFieldType(clazz: String, depth: Int = 0, extra: Field.(Any) -> Boolean = { true }): Finder =
    findFieldType(Class.forName(clazz), depth, extra)


fun findMethod(invoker: Method.(Any) -> Boolean, vararg args: Any?, depth: Int = 0): Finder {
    return Finder(Finder.Type.METHOD, invoker as Any.(Any) -> Boolean, args = args, superDepth = depth)
}

fun findMethodByReturnType(
    clazz: Class<*>,
    vararg args: Any?,
    depth: Int = 0,
    extra: Method.(Any) -> Boolean = { true }
): Finder =
    findMethod({
        returnType == clazz && extra(this, it)
    }, args = args, depth = depth)

fun findMethodByReturnType(
    clazz: String,
    vararg args: Any?,
    depth: Int = 0,
    extra: Method.(Any) -> Boolean = { true }
): Finder =
    findMethodByReturnType(Class.forName(clazz), args = args, depth = depth, extra = extra)

val cache: MutableMap<Array<out Finder>, Any> = mutableMapOf()

private fun MutableList<Field>.getFields(clazz: Class<*>, maxDepth: Int): MutableList<Field> {
    var current: Class<*> = clazz
    var currentDepth = 0
    while (maxDepth != currentDepth++) {
        addAll(current.declaredFields)
        current = current.superclass ?: return this
    }
    return this
}

private fun MutableList<Method>.getMethods(clazz: Class<*>, maxDepth: Int): MutableList<Method> {
    var current: Class<*> = clazz
    var currentDepth = 0
    while (maxDepth != currentDepth++) {
        addAll(current.declaredMethods)
        current = current.superclass ?: return this
    }
    return this
}

fun <E, T> find(start: E, vararg finders: Finder): T? {
    var current: Any = start ?: return null
    cache[finders]?.let { return it as T } // TODO bypass cache
    for (finder in finders) {
        current = when (finder.type) {
            Finder.Type.FIELD -> mutableListOf<Field>().getFields(current::class.java, finder.superDepth + 1)
                .find { finder.invoker(it, current) }?.apply { isAccessible = true }
                ?.get(current)

            Finder.Type.METHOD -> mutableListOf<Method>().getMethods(current::class.java, finder.superDepth + 1)
                .find { finder.invoker(it, current) }
                ?.apply { isAccessible = true }
                ?.invoke(current, *finder.args)
        } ?: return null
    }
    cache[finders] = current
    return current as T
}
