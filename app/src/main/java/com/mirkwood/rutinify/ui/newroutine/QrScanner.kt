package com.mirkwood.rutinify.ui.newroutine

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/*
 * Platform glue for "Escanear QR" (R-19.6, D-75): Google Play services shows its own scanner, so the app needs no
 * CAMERA permission. Cancelling does nothing; a failure (for example, no Play services) is reported.
 */

private const val TAG = "Rutinify"

fun startQrScan(context: Context, onScanned: (String) -> Unit, onFailed: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) ?: onFailed() }
        .addOnFailureListener { e ->
            Log.w(TAG, "QR scanner failed", e)
            onFailed()
        }
}
