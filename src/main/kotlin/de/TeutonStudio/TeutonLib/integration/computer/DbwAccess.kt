package de.TeutonStudio.TeutonLib.integration.computer

import de.TeutonStudio.TeutonLib.TeutonLib
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.lang.reflect.Method
import java.util.ArrayList
import net.minecraft.core.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.fml.ModList

/** Optional DBW 0.3 runtime bridge. No DBW class is linked when the mod is absent.  */
object DbwAccess {
    private var resolved = false
    private var access: Access? = null
    @JvmStatic
    private fun access(): Access? {
        if (resolved) return access
        resolved = true
        if (!ModList.get().isLoaded("drivebywire")) return null
        try {
            val c = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager")
            access = Access(
                c.getMethod("get", Level::class.java),
                c.getMethod(
                    "trySetSignalAt",
                    Level::class.java,
                    BlockPos::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ),
                c.getMethod("getSignalAt", BlockPos::class.java, Direction::class.java),
                c.getMethod("getNetwork"),
                c.getMethod(
                    "removeConnection",
                    Level::class.java,
                    BlockPos::class.java,
                    BlockPos::class.java,
                    Direction::class.java,
                    String::class.java
                ),
                c.getMethod(
                    "createConnection",
                    Level::class.java,
                    BlockPos::class.java,
                    BlockPos::class.java,
                    Direction::class.java,
                    String::class.java
                )
            )
        } catch (e: ReflectiveOperationException) {
            TeutonLib.Companion.LOGGER.error("Unsupported Drive By Wire runtime", e)
        }
        return access
    }

    @JvmStatic
    fun available(): Boolean {
        return access() != null
    }

    @JvmStatic
    fun set(host: BlockEntity, name: String, value: Int) {
        val a = access()
        if (a == null || host.level == null) return
        try {
            a.set!!.invoke(null, host.level, host.blockPos, name, value)
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("DbW signal failed", e)
        }
    }

    @JvmStatic
    fun side(host: BlockEntity, side: String): Direction {
        val front: Direction = (if (host.blockState
                .hasProperty<net.minecraft.core.Direction>(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
        )
            host.blockState
                .getValue<net.minecraft.core.Direction>(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
        else
            net.minecraft.core.Direction.NORTH)!!
        return when (side) {
            "front" -> front
            "back" -> front.opposite
            "left" -> front.clockWise
            "right" -> front.counterClockWise
            "top" -> Direction.UP
            "bottom" -> Direction.DOWN
            else -> throw IllegalArgumentException("Invalid computer side")
        }
    }

    @JvmStatic
    fun read(host: BlockEntity, side: String): Int {
        val a = access()
        if (a == null) return 0
        try {
            return a.read!!.invoke(a.get!!.invoke(null, host.level), host.blockPos, side(host, side)) as Int
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("DbW input failed", e)
        }
    }

    @Throws(ReflectiveOperationException::class)
    @JvmStatic
    private fun sinks(host: BlockEntity, name: String?): MutableList<*> {
        val a = access()
        if (a == null) return mutableListOf<Any?>()
        val network = a.network!!.invoke(a.get!!.invoke(null, host.level)) as MutableMap<*, *>
        val channels = network.get(host.blockPos.asLong())
        val sinks = if (channels is MutableMap<*, *>) channels.get(name) else null
        return if (sinks is MutableCollection<*>) java.util.List.copyOf(sinks) else mutableListOf<Any?>()
    }

    @JvmStatic
    fun connections(host: BlockEntity, name: String?): Int {
        try {
            return sinks(host, name).size
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("DbW connections failed", e)
        }
    }

    @JvmStatic
    fun rename(host: BlockEntity, oldName: String, newName: String, value: Int) {
        if (!available() || oldName == newName) return
        try {
            val sinks = sinks(host, oldName)
            val added = ArrayList<Any>()
            set(host, newName, value)
            try {
                for (sink in sinks) {
                    val pos = BlockPos.of(sink!!.javaClass.getMethod("position").invoke(sink) as Long)
                    val face = Direction.from3DDataValue(sink.javaClass.getMethod("direction").invoke(sink) as Int)
                    val result = access!!.add!!.invoke(null, host.level, host.blockPos, pos, face, newName)
                    val success = result.javaClass.getMethod("isSuccess").invoke(result) as Boolean
                    check(success) { "DbW could not migrate connection: " + result }
                    added.add(sink)
                }
            } catch (failure: ReflectiveOperationException) {
                for (sink in added) {
                    val pos = BlockPos.of(sink.javaClass.getMethod("position").invoke(sink) as Long)
                    val face = Direction.from3DDataValue(sink.javaClass.getMethod("direction").invoke(sink) as Int)
                    access!!.remove!!.invoke(null, host.level, host.blockPos, pos, face, newName)
                }
                set(host, newName, 0)
                throw failure
            } catch (failure: RuntimeException) {
                for (sink in added) {
                    val pos = BlockPos.of(sink.javaClass.getMethod("position").invoke(sink) as Long)
                    val face = Direction.from3DDataValue(sink.javaClass.getMethod("direction").invoke(sink) as Int)
                    access!!.remove!!.invoke(null, host.level, host.blockPos, pos, face, newName)
                }
                set(host, newName, 0)
                throw failure
            }
            for (sink in sinks) {
                val pos = BlockPos.of(sink!!.javaClass.getMethod("position").invoke(sink) as Long)
                val face = Direction.from3DDataValue(sink.javaClass.getMethod("direction").invoke(sink) as Int)
                access!!.remove!!.invoke(null, host.level, host.blockPos, pos, face, oldName)
            }
            set(host, oldName, 0)
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("DbW rename failed", e)
        }
    }

    @JvmStatic
    fun remove(host: BlockEntity, name: String) {
        if (!available()) return
        try {
            set(host, name, 0)
            for (sink in sinks(host, name)) {
                val pos = BlockPos.of(sink!!.javaClass.getMethod("position").invoke(sink) as Long)
                val face = Direction.from3DDataValue(sink.javaClass.getMethod("direction").invoke(sink) as Int)
                access!!.remove!!.invoke(null, host.level, host.blockPos, pos, face, name)
            }
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("DbW removal failed", e)
        }
    }

    @JvmRecord
    private data class Access(
        val get: Method,
        val set: Method,
        val read: Method,
        val network: Method,
        val remove: Method,
        val add: Method?
    )
}
