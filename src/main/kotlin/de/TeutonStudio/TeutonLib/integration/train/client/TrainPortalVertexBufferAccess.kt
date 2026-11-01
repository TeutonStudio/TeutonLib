package de.TeutonStudio.TeutonLib.integration.train.client

import java.nio.ByteBuffer

/** Kept off server class paths together with the rendering mixins. */
interface TrainPortalVertexBufferAccess {
    fun `teutonlib$uploadLighting`(vertices: ByteBuffer)
}
