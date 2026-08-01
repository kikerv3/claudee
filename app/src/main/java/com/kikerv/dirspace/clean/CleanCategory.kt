package com.kikerv.dirspace.clean

import androidx.annotation.StringRes
import com.kikerv.dirspace.R

/**
 * Qué tan seguro es borrar lo que encuentra una regla.
 *
 * Sólo las [SAFE] se marcan solas: son cosas que el sistema o las apps
 * regeneran cuando hacen falta. Las [REVIEW] pueden ser archivos que el usuario
 * quiere conservar, así que llegan sin marcar y hay que decidirlas a mano.
 */
enum class CleanRisk { SAFE, REVIEW }

enum class CleanCategory(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val risk: CleanRisk,
    val argb: Long,
) {
    TEMP_FILES(R.string.clean_cat_temp, R.string.clean_cat_temp_desc, CleanRisk.SAFE, 0xFF4A9BE8),
    THUMBNAILS(R.string.clean_cat_thumbs, R.string.clean_cat_thumbs_desc, CleanRisk.SAFE, 0xFF3DBE6E),
    TRASH(R.string.clean_cat_trash, R.string.clean_cat_trash_desc, CleanRisk.SAFE, 0xFF00B3A4),
    LOGS(R.string.clean_cat_logs, R.string.clean_cat_logs_desc, CleanRisk.SAFE, 0xFFB07A4A),
    EMPTY_FOLDERS(R.string.clean_cat_empty, R.string.clean_cat_empty_desc, CleanRisk.SAFE, 0xFF8A94A6),
    LEFTOVERS(R.string.clean_cat_leftovers, R.string.clean_cat_leftovers_desc, CleanRisk.REVIEW, 0xFFE04B5A),
    APK_INSTALLERS(R.string.clean_cat_apk, R.string.clean_cat_apk_desc, CleanRisk.REVIEW, 0xFFF2A93B),
    OLD_LARGE(R.string.clean_cat_old, R.string.clean_cat_old_desc, CleanRisk.REVIEW, 0xFF9B6BE8),
    DUPLICATES(R.string.clean_cat_dup, R.string.clean_cat_dup_desc, CleanRisk.REVIEW, 0xFFDD73C6),
}
