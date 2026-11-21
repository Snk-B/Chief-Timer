package com.snkb.chieftimer.data

import android.content.Context
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.NoticeText
import com.snkb.chieftimer.core.WidgetText
import com.snkb.chieftimer.locale.LanguageManager

/** Builds the localized [WidgetText] and [NoticeText] used by widgets and alerts, from string resources. */
object WidgetTexts {
    fun get(context: Context): WidgetText {
        val r = LanguageManager.wrap(context).resources
        return WidgetText(
            day = r.getString(R.string.time_day_short),
            hour = r.getString(R.string.time_hour_short),
            minute = r.getString(R.string.time_minute_short),
            justNow = r.getString(R.string.widget_just_now),
            agoFormat = r.getString(R.string.widget_ago_format),
            completed = r.getString(R.string.widget_completed),
            idle = r.getString(R.string.widget_idle),
            builders = r.getString(R.string.widget_builders),
            goblinSuffix = r.getString(R.string.widget_goblin_suffix),
            bite = r.getString(R.string.widget_bite),
            soup = r.getString(R.string.widget_soup),
            plusOne = r.getString(R.string.widget_plus_one),
            syncFailed = r.getString(R.string.widget_sync_failed),
            noData = r.getString(R.string.widget_no_data),
            synced = r.getString(R.string.widget_synced),
            helpers = r.getString(R.string.widget_helpers),
            apprenticeName = r.getString(R.string.widget_apprentice_name),
            assistantName = r.getString(R.string.widget_assistant_name),
            apprenticeIdle = r.getString(R.string.widget_apprentice_idle),
            assistantIdle = r.getString(R.string.widget_assistant_idle),
            laboratory = r.getString(R.string.widget_laboratory),
            petHouse = r.getString(R.string.widget_pet_house),
            newSuffix = r.getString(R.string.widget_new),
            unknownName = r.getString(R.string.widget_unknown_name),
            craftedDefense = r.getString(R.string.widget_crafted_defense),
            moduleLabel = r.getString(R.string.widget_module_label),
            townHallTag = r.getString(R.string.widget_th_tag),
        )
    }

    fun noticeText(context: Context): NoticeText {
        val r = LanguageManager.wrap(context).resources
        return NoticeText(
            builderFree = r.getString(R.string.notif_finish_builder_free),
            goblinDone = r.getString(R.string.notif_finish_goblin_done),
            bbBuilderFree = r.getString(R.string.notif_finish_bb_builder_free),
            labFree = r.getString(R.string.notif_finish_lab_free),
            starLabFree = r.getString(R.string.notif_finish_star_lab_free),
            petFree = r.getString(R.string.notif_finish_pet_free),
            itemBuilt = r.getString(R.string.notif_finish_built),
            itemReachedLevel = r.getString(R.string.notif_finish_reached_level),
            labStillFreeTitle = r.getString(R.string.notif_idle_lab_title),
            labStillFreeText = r.getString(R.string.notif_idle_lab_text),
            builderStillFreeTitle = r.getString(R.string.notif_idle_builder_title),
            builderStillFreeText = r.getString(R.string.notif_idle_builder_text),
            clockTowerTitle = r.getString(R.string.notif_clock_ready_title),
            clockTowerText = r.getString(R.string.notif_clock_ready_text),
        )
    }
}
