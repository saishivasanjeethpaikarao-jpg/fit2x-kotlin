package com.fit2x.app.diet

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * Barcode scanning for packaged foods.
 *
 * Flow: [rememberBarcodeScanLauncher] -> [ScanBarcodeContract] ->
 * [BarcodeScannerActivity] -> barcode string -> [FoodRepository.lookupBarcode].
 *
 * Works offline for the camera/scan part; nutrition lookup needs network
 * (Open Food Facts) and reports [FoodRepository.BarcodeLookupResult.Offline]
 * honestly when there is none.
 */
object BarcodeScanner {
    internal const val EXTRA_BARCODE = "com.fit2x.app.diet.EXTRA_BARCODE"
    private const val REQUEST_CAMERA = 41

    /** True when the device has any camera. Declared optional in the manifest. */
    fun isCameraAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

/**
 * Launches [BarcodeScannerActivity] and delivers the scanned barcode string,
 * or null when the user cancelled / permission was denied.
 */
class ScanBarcodeContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, BarcodeScannerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        intent?.takeIf { resultCode == Activity.RESULT_OK }
            ?.getStringExtra(BarcodeScanner.EXTRA_BARCODE)
}

/** Compose helper: `val scan = rememberBarcodeScanLauncher { code -> ... }; scan.launch(Unit)`. */
@Composable
fun rememberBarcodeScanLauncher(onResult: (String?) -> Unit) =
    rememberLauncherForActivityResult(ScanBarcodeContract(), onResult)

/**
 * Full-screen camera scanner. Requests [Manifest.permission.CAMERA] at runtime
 * on first launch; finishes cancelled when denied.
 *
 * Declared in AndroidManifest with portrait orientation.
 */
class BarcodeScannerActivity : Activity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeView = DecoratedBarcodeView(this).apply {
            // Packaged-food formats first; QR kept for good measure.
            decoderFactory = DefaultDecoderFactory(
                listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.QR_CODE
                )
            )
            setStatusText("Point at the barcode")
        }
        setContentView(barcodeView)

        barcodeView.decodeContinuous { result ->
            if (handled) return@decodeContinuous
            val text = result?.text?.trim().orEmpty()
            if (text.isEmpty()) return@decodeContinuous
            handled = true
            setResult(
                RESULT_OK,
                Intent().putExtra(BarcodeScanner.EXTRA_BARCODE, text)
            )
            finish()
        }

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                BarcodeScanner.REQUEST_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BarcodeScanner.REQUEST_CAMERA &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            // No camera permission, no scan — bail out cleanly.
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}
