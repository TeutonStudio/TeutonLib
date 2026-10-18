package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.core.asm.PeripheralMethodSupplier
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral
import de.TeutonStudio.TeutonLib.api.computer.*
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Modifier
import java.util.Optional

/** Uses the same method supplier as CC, including dynamic and generic peripherals. */
object SourceIntrospection {
    private val methods = PeripheralMethodSupplier.create(emptyList())
    private val signatures = object : ClassValue<Map<String, SourceMethod>>() {
        override fun computeValue(type: Class<*>): Map<String, SourceMethod> = buildMap {
            type.methods.sortedBy { it.toGenericString() }.forEach { method ->
                val annotation = method.getAnnotation(LuaFunction::class.java) ?: return@forEach
                val names = annotation.value.toList().ifEmpty { listOf(method.name) }
                val parameterNames = parameterNames(method)
                val arguments = method.parameters.mapIndexedNotNull { index, parameter ->
                    when (parameter.type) {
                        ILuaContext::class.java, IComputerAccess::class.java -> null
                        IArguments::class.java -> "...: unknown"
                        else -> "${parameterNames.getOrNull(index) ?: "arg${index + 1}"}: ${luaType(parameter.parameterizedType)}"
                    }
                }.joinToString(", ")
                names.forEach { name -> put(name, SourceMethod(name, "$name($arguments) -> ${luaType(method.genericReturnType)}")) }
            }
        }
    }

    private fun parameterNames(method: Method): List<String> {
        if (method.parameters.all { it.isNamePresent }) return method.parameters.map { it.name }
        val owner = method.declaringClass
        val node = ClassNode()
        val stream = owner.getResourceAsStream("/${owner.name.replace('.', '/')}.class") ?: return emptyList()
        stream.use { ClassReader(it).accept(node, ClassReader.SKIP_FRAMES) }
        val bytecode = node.methods.firstOrNull { it.name == method.name && it.desc == Type.getMethodDescriptor(method) }
            ?: return emptyList()
        var slot = if (Modifier.isStatic(method.modifiers)) 0 else 1
        return method.parameterTypes.mapIndexed { index, type ->
            val name = bytecode.localVariables?.firstOrNull { it.index == slot }?.name ?: "arg${index + 1}"
            slot += if (type == Long::class.javaPrimitiveType || type == Double::class.javaPrimitiveType) 2 else 1
            name
        }
    }

    private fun luaType(type: java.lang.reflect.Type): String {
        if (type is ParameterizedType) {
            if (type.rawType == Optional::class.java) return luaType(type.actualTypeArguments.first()) + "|nil"
            return luaType(type.rawType)
        }
        if (type !is Class<*>) return "unknown"
        return when {
            type == Void.TYPE -> "void"
            type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java -> "boolean"
            type in listOf<Class<*>?>(Byte::class.javaPrimitiveType, Short::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, java.lang.Byte::class.java, java.lang.Short::class.java,
                java.lang.Integer::class.java, java.lang.Long::class.java) -> "integer"
            type == Float::class.javaPrimitiveType || type == Double::class.javaPrimitiveType || Number::class.java.isAssignableFrom(type) -> "number"
            type == String::class.java || type == java.nio.ByteBuffer::class.java -> "string"
            type == MethodResult::class.java || type == Array<Any>::class.java -> "unknown"
            type == Any::class.java -> "any"
            type.isArray || Map::class.java.isAssignableFrom(type) || Collection::class.java.isAssignableFrom(type) -> "table"
            else -> "unknown"
        }
    }

    fun methodNames(peripheral: IPeripheral): List<String> =
        methods.getSelfMethods(peripheral).keys.sorted()

    fun documentedMethods(peripheral: IPeripheral?, documentation: List<SourceMethod>): List<SourceMethod> {
        val docs = documentation.associateBy { it.name }
        val names = if (peripheral == null) docs.keys.sorted() else methodNames(peripheral)
        val inferred = peripheral?.let { signatures.get(it.javaClass) }.orEmpty()
        return names.map { name ->
            val documentation = docs[name]
            if (documentation != null && documentation.signature.isNotBlank()) documentation
            else SourceMethod(name, inferred[name]?.signature ?: "$name(...: unknown) -> unknown", documentation?.description.orEmpty())
        }
    }

    fun modemDetails(peripheral: IPeripheral?, dimension: String): ModemDetails? =
        (peripheral as? ModemPeripheral)?.let {
            val wireless = it is WirelessModemPeripheral || it.isWireless
            ModemDetails(
                if (!wireless) "wired" else if (it.isInterdimensional) "ender" else "radio",
                dimension, if (wireless) it.range else null, it.isInterdimensional
            )
        }

    fun describe(runtime: ComputerRuntime, view: SourceView, peripheral: IPeripheral? = runtime.peripherals()[view.name]): SourceApi {
        val docs = ComputerExtensions.sourceDocumentation(runtime, view)
        return SourceApi(view.name, view.types, view.connection,
            documentedMethods(peripheral, docs),
            modemDetails(peripheral, runtime.level().dimension().location().toString()),
            view.devicePosition, view.linkPosition)
    }
}
