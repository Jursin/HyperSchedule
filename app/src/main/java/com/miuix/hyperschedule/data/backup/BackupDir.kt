package com.miuix.hyperschedule.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.IOException

/** 备份目录里的一项 */
data class BackupEntry(
    val name: String,
    val size: Long,
)

/** 备份文件的存放位置，只走 SAF，文件写进所选目录下的 [BACKUP_FOLDER] 子目录 */
interface BackupDir {
    /** 展示给用户的位置说明 */
    val label: String

    /** 存进设置的位置，content:// 树 uri */
    val location: String

    /** 目录是否仍然可读，权限被回收或目录被删都返回 false */
    fun isAccessible(): Boolean

    fun list(): List<BackupEntry>

    /** 覆盖同名文件 */
    fun write(name: String, text: String)

    fun read(name: String): String

    fun delete(name: String)
}

/** 应用建的子目录名 */
const val BACKUP_FOLDER = "HyperSchedule"

/** 外部存储提供者与内部存储卷的标识，用来把 document id 还原成看得懂的路径 */
private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val INTERNAL_VOLUME = "primary:"
private const val INTERNAL_VOLUME_LABEL = "内部存储"

/** 备份目录解析 */
object BackupDirs {

    /** 所选目录的展示名，取不到路径时只返回文件夹名 */
    fun labelOf(context: Context, treeUri: Uri): String? {
        val name =
            runCatching {
                    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    context.contentResolver
                        .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }
                .getOrNull()
        val path = pathOf(treeUri)
        return when {
            path != null -> if (name == BACKUP_FOLDER) path else "$path/$BACKUP_FOLDER"
            name.isNullOrBlank() -> null
            name == BACKUP_FOLDER -> name
            else -> "$name/$BACKUP_FOLDER"
        }
    }

    private fun pathOf(treeUri: Uri): String? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (!documentId.startsWith(INTERNAL_VOLUME)) return null
        val relative = documentId.removePrefix(INTERNAL_VOLUME)
        return if (relative.isBlank()) INTERNAL_VOLUME_LABEL else "$INTERNAL_VOLUME_LABEL/$relative"
    }

    /** 按存下来的位置字符串还原目录；返回 null 表示没有配置或位置已无法解析 */
    fun from(context: Context, location: String?, label: String): BackupDir? {
        if (location.isNullOrBlank()) return null
        val uri = runCatching { location.toUri() }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        // 展示名每次现算，存下来的旧值可能已不合适
        val shown = labelOf(context, uri) ?: label.ifBlank { "已选择的文件夹" }
        return runCatching { SafBackupDir(context, uri, shown) }.getOrNull()
    }
}

/** SAF 目录，文件落在所选目录下的 HyperSchedule */
class SafBackupDir(
    private val context: Context,
    private val treeUri: Uri,
    override val label: String,
) : BackupDir {

    override val location: String get() = treeUri.toString()

    private val resolver: ContentResolver get() = context.contentResolver

    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    /** 子目录的 document id；首次访问时按需创建 */
    private var folderDocId: String? = null

    private fun childrenUri(documentId: String) =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

    private fun documentUri(documentId: String) =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun query(documentId: String, columns: Array<String>): List<Row> {
        val rows = mutableListOf<Row>()
        resolver
            .query(childrenUri(documentId), columns, null, null, null)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    rows +=
                        Row(
                            id = cursor.getString(idIndex),
                            name = cursor.getString(nameIndex).orEmpty(),
                            isFolder = cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                        )
                }
            }
        return rows
    }

    /** 定位或创建存放备份的子目录 */
    private fun folderDocId(): String {
        folderDocId?.let { return it }
        if (displayNameOf(rootDocId) == BACKUP_FOLDER) {
            // 用户选的正好是 HyperSchedule，直接用
            return rootDocId.also { folderDocId = it }
        }
        val existing = query(rootDocId, NAME_COLUMNS).firstOrNull { it.isFolder && it.name == BACKUP_FOLDER }
        val id =
            existing?.id
                ?: run {
                    val created =
                        DocumentsContract.createDocument(
                            resolver,
                            documentUri(rootDocId),
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            BACKUP_FOLDER,
                        ) ?: throw IOException("无法在所选目录下创建 $BACKUP_FOLDER")
                    DocumentsContract.getDocumentId(created)
                }
        return id.also { folderDocId = it }
    }

    private fun displayNameOf(documentId: String): String? {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(documentUri(documentId), columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun files(): List<Row> = query(folderDocId(), FULL_COLUMNS).filter { !it.isFolder }

    private fun fileId(name: String): String? = files().firstOrNull { it.name == name }?.id

    override fun isAccessible(): Boolean =
        runCatching {
                // 父目录被删时 provider 会返回空列表，先确认所选的根文档本身还在
                displayNameOf(rootDocId) != null
            }
            .getOrDefault(false)

    override fun list(): List<BackupEntry> =
        files().map { BackupEntry(name = it.name, size = it.size) }

    override fun write(name: String, text: String) {
        val folder = folderDocId()
        val tempName = "$name.part"
        // 清掉上次失败留下的临时文件
        fileId(tempName)?.let { runCatching { DocumentsContract.deleteDocument(resolver, documentUri(it)) } }
        val created =
            DocumentsContract.createDocument(resolver, documentUri(folder), "application/json", tempName)
                ?: throw IOException("无法创建 $name")
        var written = false
        try {
            resolver.openOutputStream(created)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IOException("无法写入 $name")
            written = true
            // 新文件写完后再删旧备份，最后改成正式文件名
            fileId(name)?.let { DocumentsContract.deleteDocument(resolver, documentUri(it)) }
            if (DocumentsContract.renameDocument(resolver, created, name) == null) {
                throw IOException("无法保存 $name，已保留临时文件")
            }
        } catch (e: Exception) {
            // 新文件还没写完才删临时文件，写完后就留着方便恢复
            if (!written) runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
    }

    override fun read(name: String): String {
        val id = fileId(name) ?: throw IOException("找不到 $name")
        return resolver.openInputStream(documentUri(id))?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("无法读取 $name")
    }

    override fun delete(name: String) {
        val id = fileId(name) ?: return
        DocumentsContract.deleteDocument(resolver, documentUri(id))
    }

    private class Row(
        val id: String,
        val name: String,
        val isFolder: Boolean,
        val size: Long,
    )

    private companion object {
        val NAME_COLUMNS =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )

        val FULL_COLUMNS =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
    }
}
