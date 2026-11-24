package com.snkb.chieftimer.sync

import android.content.ClipboardManager
import android.content.Context
import com.snkb.chieftimer.core.ExportParser
import com.snkb.chieftimer.core.ParseResult
import com.snkb.chieftimer.core.SyncLogic
import com.snkb.chieftimer.core.describe
import com.snkb.chieftimer.core.normalizeTag
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.widget.WidgetUpdater
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One sync pipeline for both the widget's Sync button and the launcher screen's button. */
object SyncRunner {

    data class Result(val ok: Boolean, val message: String)

    /**
     * Reads the primary clip as text. Must be called while the calling activity has window focus
     * (Android 10+ only lets the focused app read the clipboard). Returns null if empty or blocked.
     */
    fun readClipboard(context: Context): String? = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    } catch (e: Exception) {
        null
    }

    /**
     * Parses [clipboard], saves the new state to the account matching export tag, logs the result and refreshes widgets.
     */
    suspend fun sync(context: Context, clipboard: String?, source: String): Result = withContext(NonCancellable) {
        val app = context.applicationContext
        val repo = StateRepository(app)
        val lang = com.snkb.chieftimer.locale.LanguageManager.language(context)
        val isPt = lang == "pt"
        val isRu = lang == "ru"
        val lctx = com.snkb.chieftimer.locale.LanguageManager.wrap(context)
        val result = try {
            if (clipboard.isNullOrBlank()) {
                val msg = if (isPt) "Falha na sincronização: A área de transferência está vazia"
                          else if (isRu) "Ошибка синхронизации: Буфер обмена пуст"
                          else "Sync failed: Clipboard is empty"
                Result(false, msg)
            } else {
                val export = when (val r = ExportParser.parse(clipboard)) {
                    is ParseResult.Invalid -> {
                        val translatedReason = when {
                            r.reason.contains("Not JSON", ignoreCase = true) -> {
                                if (isPt) "O texto copiado não é um formato JSON válido"
                                else if (isRu) "Текст не является форматом JSON"
                                else r.reason
                            }
                            r.reason.contains("Not a JSON object", ignoreCase = true) -> {
                                if (isPt) "O texto copiado não é um objeto JSON válido"
                                else if (isRu) "Текст не является объектом JSON"
                                else r.reason
                            }
                            r.reason.contains("Missing buildings", ignoreCase = true) -> {
                                if (isPt) "Lista de construções ausente nos dados"
                                else if (isRu) "Список построек отсутствует"
                                else r.reason
                            }
                            r.reason.contains("Missing or invalid tag", ignoreCase = true) -> {
                                if (isPt) "Tag de jogador ausente ou inválida"
                                else if (isRu) "Тег игрока отсутствует или неверен"
                                else r.reason
                            }
                            r.reason.contains("Missing or invalid timestamp", ignoreCase = true) -> {
                                if (isPt) "Data/hora da vila ausente"
                                else if (isRu) "Временная метка отсутствует"
                                else r.reason
                            }
                            else -> r.reason
                        }
                        val prefix = if (isPt) "Falha na sincronização: Os dados não são de uma vila válida"
                                     else if (isRu) "Ошибка синхронизации: Неверные данные деревни"
                                     else "Sync failed: Not village data"
                        val msg = "$prefix: $translatedReason"
                        repo.log(source, false, msg)
                        return@withContext Result(false, plainText(msg))
                    }
                    is ParseResult.Success -> r.export
                }
                val tag = normalizeTag(export.tag)
                val outcome = repo.syncVillage(tag, export, clipboard)

                val ok = outcome is SyncLogic.Outcome.Synced
                if (ok) {
                    runCatching {
                        val maxData = com.snkb.chieftimer.data.MaxDataStore.get(app)
                        com.snkb.chieftimer.core.RoadToMaxCalc.compute(maxData, export)?.let { road ->
                            val overallPct = ((road.work.overall ?: 1.0) * 100).toInt().coerceIn(0, 100)
                            val wallsPct = ((road.work.walls.done ?: 1.0) * 100).toInt().coerceIn(0, 100)
                            repo.appendVillageSnapshot(
                                tag,
                                com.snkb.chieftimer.core.VillageSnapshot(
                                    at = export.timestamp,
                                    townHall = road.townHall,
                                    overallPct = overallPct,
                                    wallsPct = wallsPct,
                                    builderHoursLeft = road.builderWork.seconds / 3600L,
                                ),
                            )
                        }
                    }
                }
                Result(ok, plainText(outcome.describe(context, Names.get(app))))
            }
        } catch (t: Throwable) {
            val prefix = if (isPt) "Erro" else if (isRu) "Ошибка" else "Error"
            Result(false, "$prefix: ${t.javaClass.simpleName}: ${t.message}")
        }
        repo.log(source, result.ok, result.message)
        refreshWidgets(app, source)
        result
    }

    /** Re-renders every placed widget; logs failures and how many widgets are placed. */
    suspend fun refreshWidgets(context: Context, source: String): Int {
        val count = WidgetUpdater.updateAll(context, source)
        if (count == 0) StateRepository(context).log(source, null, "No Chief Timer widget is placed on the home screen")
        return count
    }

    /**
     * Text shown by system UI (toasts, the launcher screen) uses the phone's font, which draws "→"
     * as "ʼn" on a HiOS (Tecno) phone (the APK's strings are correct UTF-8). Use ASCII there.
     */
    fun plainText(s: String): String = s.replace(" → ", " -> ").replace("→", "->")
}
