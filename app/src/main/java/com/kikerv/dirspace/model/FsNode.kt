package com.kikerv.dirspace.model

import java.io.File

/**
 * Nodo del árbol de archivos construido durante el escaneo.
 *
 * Se usa una única clase mutable (en vez de una jerarquía sellada de data classes)
 * porque en un teléfono con muchos archivos se llegan a crear cientos de miles de
 * instancias y cada campo extra cuenta. Las rutas no se guardan: se reconstruyen
 * subiendo por [parent], lo que ahorra decenas de MB en volúmenes grandes.
 */
class FsNode(
    @JvmField val name: String,
    @JvmField val parent: FsNode?,
    @JvmField val isDirectory: Boolean,
    /** Ruta absoluta; sólo la guarda el nodo raíz. */
    private val rootPath: String? = null,
) {
    /** Bytes ocupados: tamaño del archivo o suma recursiva de la carpeta. */
    @JvmField var size: Long = 0L

    /** Número de archivos contenidos (recursivo). Para un archivo siempre es 1. */
    @JvmField var fileCount: Int = 0

    /** Número de subcarpetas contenidas (recursivo). */
    @JvmField var dirCount: Int = 0

    @JvmField var lastModified: Long = 0L

    /** Hijos ordenados de mayor a menor tamaño. `null` en archivos. */
    @JvmField var children: MutableList<FsNode>? = null

    val path: String
        get() {
            val p = parent ?: return rootPath ?: name
            val sb = StringBuilder(64)
            buildPath(sb)
            return sb.toString()
        }

    private fun buildPath(sb: StringBuilder) {
        val p = parent
        if (p == null) {
            sb.append(rootPath ?: name)
            return
        }
        p.buildPath(sb)
        if (sb.isNotEmpty() && sb[sb.length - 1] != File.separatorChar) {
            sb.append(File.separatorChar)
        }
        sb.append(name)
    }

    val file: File get() = File(path)

    val depth: Int
        get() {
            var d = 0
            var n = parent
            while (n != null) {
                d++
                n = n.parent
            }
            return d
        }

    val isRoot: Boolean get() = parent == null

    /** Extensión en minúsculas y sin punto; cadena vacía si no tiene. */
    val extension: String
        get() {
            if (isDirectory) return ""
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return ""
            val ext = name.substring(dot + 1)
            // Evita tratar "archivo.con.nombre largo" como extensión.
            if (ext.length > 12) return ""
            return ext.lowercase()
        }

    val category: FileCategory
        get() = if (isDirectory) FileCategory.FOLDER else FileCategory.of(extension)

    /** Fracción que representa este nodo dentro de su carpeta padre (0..1). */
    fun fractionOfParent(): Float {
        val p = parent ?: return 1f
        if (p.size <= 0L) return 0f
        return (size.toDouble() / p.size.toDouble()).toFloat()
    }

    /** Cadena de ancestros desde la raíz hasta este nodo, ambos incluidos. */
    fun pathChain(): List<FsNode> {
        val chain = ArrayList<FsNode>(8)
        var n: FsNode? = this
        while (n != null) {
            chain.add(n)
            n = n.parent
        }
        chain.reverse()
        return chain
    }

    /** Descuenta [bytes] y [files] de este nodo y de todos sus ancestros. */
    fun propagateRemoval(bytes: Long, files: Int, dirs: Int) {
        var n: FsNode? = parent
        while (n != null) {
            n.size -= bytes
            n.fileCount -= files
            n.dirCount -= dirs
            n = n.parent
        }
    }

    override fun toString(): String = path
}
