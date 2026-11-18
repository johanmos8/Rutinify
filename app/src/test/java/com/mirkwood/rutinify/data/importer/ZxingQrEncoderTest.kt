package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.mirkwood.rutinify.domain.importer.PlanLink
import org.junit.Test

/** T-59: the QR on screen decodes back to the plan link (R-19.4). */
class ZxingQrEncoderTest {

    @Test
    fun `el QR generado se lee y devuelve el enlace`() { // R-19.4, D-75
        val link = (PlanLink.encode("""{"schemaVersion":1,"routine":{"name":"Gato","date":null,"notes":null},"items":[]}""") as PlanLink.Encoded.Ok).link
        val qr = ZxingQrEncoder().encode(link)

        // Draw it 4 pixels per module with a quiet zone, as the screen does
        val scale = 4
        val quiet = 4
        val side = (qr.size + 2 * quiet) * scale
        val pixels = IntArray(side * side) { i ->
            val x = i % side / scale - quiet
            val y = i / side / scale - quiet
            if (x in 0 until qr.size && y in 0 until qr.size && qr[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))

        assertThat(QRCodeReader().decode(bitmap).text).isEqualTo(link)
    }
}
