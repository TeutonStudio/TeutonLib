package de.TeutonStudio.TeutonLib.traintest.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.TeutonStudio.TeutonLib.integration.train.TrainPortalProjectedSupport
import de.TeutonStudio.TeutonLib.traintest.TrainRiderDiagnostics
import net.minecraft.client.Minecraft
import qouteall.imm_ptl.core.compat.sable_straddle.IPStraddleCut
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions
import qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender

/** Compares active carry sessions with the retired sessions native collision still accepts. */
object TrainRiderClientDiagnostics {
    fun tick() {
        if (!TrainRiderDiagnostics.enabled) return
        TrainRiderDiagnostics.observe {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return@observe
            if (player.isSpectator) return@observe
            val active = IPStraddleClientSessions.all().mapTo(hashSetOf()) { it.ship() to it.portal() }
            val row = TrainRiderDiagnostics.base("client-post-tick").apply {
                addProperty("gameTime", player.level().gameTime)
                add("player", TrainRiderDiagnostics.entity(player))
                add("sessions", JsonArray().apply {
                    for (session in IPStraddleClientSessions.renderSessions()) {
                        add(JsonObject().apply {
                            addProperty("ship", session.ship().toString())
                            addProperty("portal", session.portal().toString())
                            addProperty("active", (session.ship() to session.portal()) in active)
                            addProperty("shipDimension", session.shipDim().location().toString())
                            addProperty("destination", session.destDim().location().toString())
                            add("origin", TrainRiderDiagnostics.point(session.origin()))
                            add("normal", TrainRiderDiagnostics.point(session.normal()))
                            add("destinationOrigin", TrainRiderDiagnostics.point(session.destOrigin()))
                            add("destinationNormal", TrainRiderDiagnostics.point(session.destNormal()))
                            val ship = IPStraddleRender.shipOf(session)
                            if (ship != null) {
                                add("body", TrainRiderDiagnostics.ship(ship))
                                add("renderPose", TrainRiderDiagnostics.pose(ship.renderPose()))
                                val current = session.isometry().mapPose(ship.logicalPose())
                                val before = session.isometry().mapPose(ship.lastPose())
                                add("mappedCurrent", TrainRiderDiagnostics.pose(current))
                                add("mappedLast", TrainRiderDiagnostics.pose(before))
                                add("mappedLastDisplacement", TrainRiderDiagnostics.point(
                                    TrainPortalProjectedSupport.displacement(before, current, player.position())))
                                val projection = IPStraddleCut.Projection(ship, session.isometry(), session.destOrigin(),
                                    session.destNormal(), session.portal())
                                addProperty("supportsAtLast", TrainPortalProjectedSupport.supportsFloor(ship, before, player.position()) {
                                    projection.keepsBlock(before, it)
                                })
                                addProperty("supportsAtCurrent", TrainPortalProjectedSupport.supportsFloor(ship, current, player.position()) {
                                    projection.keepsBlock(current, it)
                                })
                            }
                        })
                    }
                })
            }
            TrainRiderDiagnostics.write(mc.gameDirectory.toPath().resolve("train-portal-verification/riders-client.jsonl"), row)
        }
    }
}
