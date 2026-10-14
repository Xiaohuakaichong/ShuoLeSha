package com.example.shuolesa.domain

import android.net.Uri
import androidx.room.withTransaction
import com.example.shuolesa.AppContainer
import com.example.shuolesa.service.AudioCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A portable, credential-free snapshot; never modifies the live database. */
class BackupMemory(private val app:AppContainer) {
    suspend operator fun invoke(destination:Uri)=withContext(Dispatchers.IO) {app.processing.mutateSession {
        check(!AudioCaptureService.state.value.active) { "请先停止录音，再导出完整备份" }
        val folder=File(app.context.cacheDir,"backup-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val database=app.context.getDatabasePath("shuolesa_db")
            // Room's write transaction holds a stable WAL while its database files are copied.
            app.database.withTransaction {
                listOf(database,File(database.path+"-wal")).filter { it.exists() }.forEach { source ->
                    source.copyTo(File(folder,source.name),true)
                }
            }
            val root=app.context.filesDir.canonicalFile
            val sessions=app.repository.dao.observeSessions().first()
            val paths=sessions.flatMap { session -> app.repository.audio.chunks(session.id).map { it.filePath }+
                listOfNotNull(session.sourcePath) }.distinct()
            val output=app.context.contentResolver.openOutputStream(destination) ?: error("无法写入选择的位置")
            ZipOutputStream(output.buffered()).use { zip ->
                fun entry(name:String,file:File) { zip.putNextEntry(ZipEntry(name));file.inputStream().use { it.copyTo(zip) };zip.closeEntry() }
                folder.listFiles().orEmpty().forEach { entry("database/${it.name}",it) }
                val manifest=JSONObject().put("version",4).put("schema",6).put("created_at",System.currentTimeMillis())
                    .put("original_files_root",root.path).put("restoration","Restore database with WAL together; remap original_files_root to the destination app files directory.")
                zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString(2).toByteArray());zip.closeEntry()
                paths.forEach { path -> val file=File(path).canonicalFile
                    if(file.isFile && file.path.startsWith(root.path+File.separator))
                        entry("files/"+file.relativeTo(root).invariantSeparatorsPath,file)
                }
            }
        } finally { folder.listFiles().orEmpty().forEach { it.delete() };folder.delete() }
    }}
}
