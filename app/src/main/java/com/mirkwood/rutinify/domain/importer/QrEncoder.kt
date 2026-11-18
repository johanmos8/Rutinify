package com.mirkwood.rutinify.domain.importer

/** A QR code as a square of dark ([get] true) and light modules, ready to draw (R-19.4). */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/** Builds QR codes on the phone (D-75). */
interface QrEncoder {
    fun encode(text: String): QrMatrix
}
