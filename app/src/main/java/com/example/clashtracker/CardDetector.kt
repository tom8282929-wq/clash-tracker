package com.example.clashtracker

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Template-matches a captured region against reference card icons to figure
 * out which card the opponent just played.
 *
 * SETUP REQUIRED (not included — Supercell's card art isn't something I can
 * bundle for you): drop your own cropped card-icon screenshots into
 * app/src/main/assets/templates/<CardName>.png, one per card, cropped
 * consistently (same size/zoom) from the opponent's "just played" slot.
 *
 * This class only handles the matching logic — you still need to:
 *  1. Calibrate CAPTURE_REGION per your device's resolution/aspect ratio.
 *  2. Supply the template PNGs.
 *  3. Tune MATCH_THRESHOLD against false positives/negatives.
 */
class CardDetector(private val context: Context) {

    companion object {
        const val MATCH_THRESHOLD = 0.75
    }

    private val templates: MutableMap<String, Mat> = mutableMapOf()

    fun loadTemplates() {
        val templateDir = "templates"
        val files = context.assets.list(templateDir) ?: return
        for (fileName in files) {
            val cardName = fileName.substringBeforeLast(".")
            context.assets.open("$templateDir/$fileName").use { stream ->
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                val mat = Mat()
                Utils.bitmapToMat(bmp, mat)
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
                templates[cardName] = mat
            }
        }
    }

    /**
     * Runs template matching for the given cropped region bitmap (the
     * "just played" card slot from your capture pipeline).
     * Returns the best-matching card name and a 0..1 confidence, or
     * null if nothing clears MATCH_THRESHOLD.
     */
    fun detect(region: Bitmap): Pair<String, Double>? {
        val sceneMat = Mat()
        Utils.bitmapToMat(region, sceneMat)
        Imgproc.cvtColor(sceneMat, sceneMat, Imgproc.COLOR_RGBA2RGB)

        var bestName: String? = null
        var bestScore = 0.0

        for ((name, template) in templates) {
            if (template.rows() > sceneMat.rows() || template.cols() > sceneMat.cols()) continue

            val result = Mat()
            Imgproc.matchTemplate(sceneMat, template, result, Imgproc.TM_CCOEFF_NORMED)
            val mmr = Core.minMaxLoc(result)
            if (mmr.maxVal > bestScore) {
                bestScore = mmr.maxVal
                bestName = name
            }
            result.release()
        }

        sceneMat.release()

        return if (bestName != null && bestScore >= MATCH_THRESHOLD) {
            bestName to bestScore
        } else {
            null
        }
    }
}
