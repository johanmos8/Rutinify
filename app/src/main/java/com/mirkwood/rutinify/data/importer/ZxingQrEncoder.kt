package com.mirkwood.rutinify.data.importer

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mirkwood.rutinify.domain.importer.QrEncoder
import com.mirkwood.rutinify.domain.importer.QrMatrix
import javax.inject.Inject

/** ZXing `core` with error correction M, the level the 2,331-byte limit assumes (R-19.5, D-75). */
class ZxingQrEncoder @Inject constructor() : QrEncoder {

    override fun encode(text: String): QrMatrix {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        // Size 0 asks for one pixel per module: the screen scales it
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        return QrMatrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
    }
}
