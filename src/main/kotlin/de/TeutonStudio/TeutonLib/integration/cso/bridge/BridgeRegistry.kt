package de.TeutonStudio.TeutonLib.integration.cso.bridge

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import java.util.IdentityHashMap

object BridgeRegistry {
    private val modifiers = IdentityHashMap<KineticBlockEntity, MutableMap<KineticBlockEntity, Float>>()
    fun replace(edges: List<GearBridgeManager.DirectedEdge>) {
        modifiers.clear()
        edges.forEach { edge -> modifiers.getOrPut(edge.from) { IdentityHashMap() }[edge.to] = edge.modifier }
    }
    fun modifier(from: KineticBlockEntity, to: KineticBlockEntity): Float? = modifiers[from]?.get(to)
    fun neighbours(from: KineticBlockEntity): List<KineticBlockEntity> = modifiers[from]?.keys?.toList() ?: emptyList()
}
