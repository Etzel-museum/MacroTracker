package com.davidlevi.macros

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import java.time.LocalDate

/** Export/import the whole data file through the system file picker (no permissions needed). */
class Backup(private val a: MainActivity) {
    private val reqExport = 41
    private val reqImport = 42

    fun export() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "macros-backup-${LocalDate.now()}.json")
        }
        a.startActivityForResult(i, reqExport)
    }

    fun import() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        a.startActivityForResult(i, reqImport)
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            reqExport -> {
                val ok = runCatching {
                    a.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(a.store.toJson().toString(1).toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
                toast(if (ok) "הגיבוי נשמר" else "שמירת הגיבוי נכשלה")
            }
            reqImport -> {
                val text = runCatching {
                    a.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                if (text == null) { toast("לא הצלחתי לקרוא את הקובץ"); return }
                AlertDialog.Builder(a)
                    .setTitle("ייבוא גיבוי")
                    .setMessage("הייבוא מחליף את כל הנתונים שבטלפון בנתונים שבקובץ. להמשיך?")
                    .setPositiveButton("ייבוא") { _, _ ->
                        val ok = runCatching { a.store.importJson(text) }.isSuccess
                        toast(if (ok) "הגיבוי יובא" else "הקובץ אינו גיבוי תקין של מאקרו")
                        a.refreshCurrent()
                    }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()
}
