package eu.steingaming.payall.utils

import eu.steingaming.payall.PayAll
import java.lang.reflect.Field
import java.lang.reflect.Method

class Finder(
    val type: Type,
    val invoker: Any.(previous: Any) -> Boolean,
    val args: Array<out Any?>,
    val includeSuper: Boolean = false
) {
    enum class Type { FIELD, METHOD }
}

fun findField(invoker: Field.(Any) -> Boolean): Finder {
    return Finder(Finder.Type.FIELD, invoker as Any.(Any) -> Boolean, emptyArray())
}

fun findFieldType(typee: Class<*>, extra: Field.(Any) -> Boolean = { true }): Finder {
    return Finder(Finder.Type.FIELD, {
        this as Field
        type == typee && extra(this)
    }, emptyArray())
}

fun findFieldTypeWithSuper(typee: Class<*>, extra: Field.(Any) -> Boolean = { true }): Finder {
    return Finder(Finder.Type.FIELD, {
        this as Field
        type == typee && extra(this)
    }, emptyArray(), includeSuper = true)
}

fun findMethod(invoker: Method.(Any) -> Boolean, vararg args: Any?): Finder {
    return Finder(Finder.Type.METHOD, invoker as Any.(Any) -> Boolean, args)
}

fun <E, T> find(start: E, vararg finders: Finder): T? {
    var current: Any = start ?: return null
    for (finder in finders) {
        current = when (finder.type) {
            Finder.Type.FIELD -> listOf(
                *current::class.java.declaredFields,
                *(if (finder.includeSuper) (current::class.java.superclass?.declaredFields
                    ?: emptyArray()) else emptyArray())
            ).find { finder.invoker(it, current) }?.apply { isAccessible = true }
                ?.get(current)

            Finder.Type.METHOD -> listOf(
                *current::class.java.declaredMethods,
                *(if (finder.includeSuper) (current::class.java.superclass?.declaredMethods
                    ?: emptyArray()) else emptyArray())
            ).find { finder.invoker(it, current) }
                ?.apply { isAccessible = true }
                ?.invoke(current, *finder.args)
        } ?: return null
    }
    return current as T
}
