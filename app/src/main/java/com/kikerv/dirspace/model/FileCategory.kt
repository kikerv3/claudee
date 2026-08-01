package com.kikerv.dirspace.model

import androidx.annotation.StringRes
import com.kikerv.dirspace.R

/**
 * Familias de archivos usadas para colorear el mapa, igual que la lista de
 * extensiones de WinDirStat. Los colores están elegidos para distinguirse bien
 * entre sí incluso en rectángulos de pocos píxeles.
 */
enum class FileCategory(
    val argb: Long,
    @StringRes val labelRes: Int,
) {
    VIDEO(0xFFE04B5A, R.string.cat_video),
    IMAGE(0xFF3DBE6E, R.string.cat_image),
    AUDIO(0xFFF2A93B, R.string.cat_audio),
    DOCUMENT(0xFF4A9BE8, R.string.cat_document),
    ARCHIVE(0xFF9B6BE8, R.string.cat_archive),
    APP(0xFF00B3A4, R.string.cat_app),
    CODE(0xFFE8D44A, R.string.cat_code),
    DATA(0xFFB07A4A, R.string.cat_data),
    FONT(0xFFDD73C6, R.string.cat_font),
    OTHER(0xFF8A94A6, R.string.cat_other),
    FOLDER(0xFF5A6577, R.string.cat_folder),
    ;

    companion object {
        private val BY_EXTENSION: HashMap<String, FileCategory> = HashMap(256).apply {
            put(VIDEO, "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpg", "mpeg", "ts", "mts", "rmvb")
            put(IMAGE, "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tiff", "tif", "svg", "raw", "dng", "cr2", "nef", "ico", "avif")
            put(AUDIO, "mp3", "aac", "wav", "flac", "ogg", "opus", "m4a", "wma", "amr", "aiff", "mid", "midi", "m4b")
            put(DOCUMENT, "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "rtf", "md", "epub", "mobi", "azw3", "djvu", "pages", "csv")
            put(ARCHIVE, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "lz4", "zst", "tgz", "jar", "obb")
            put(APP, "apk", "apks", "xapk", "aab", "so", "dex", "vdex", "odex", "art", "oat")
            put(CODE, "java", "kt", "kts", "js", "ts", "tsx", "jsx", "py", "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "sh", "bash", "html", "htm", "css", "scss", "xml", "json", "yaml", "yml", "toml", "gradle", "sql", "lua", "dart")
            put(DATA, "db", "sqlite", "sqlite3", "realm", "dat", "bin", "cache", "log", "bak", "tmp", "idx", "pack", "blob", "journal", "wal")
            put(FONT, "ttf", "otf", "woff", "woff2", "ttc", "fon")
        }

        private fun HashMap<String, FileCategory>.put(category: FileCategory, vararg extensions: String) {
            for (e in extensions) put(e, category)
        }

        fun of(extension: String): FileCategory {
            if (extension.isEmpty()) return OTHER
            return BY_EXTENSION[extension] ?: OTHER
        }
    }
}
