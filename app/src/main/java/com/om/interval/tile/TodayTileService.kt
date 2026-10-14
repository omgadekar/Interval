package com.om.interval.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.om.interval.MainActivity
import com.om.interval.data.Prefs
import com.om.interval.data.LibraryStore
import com.om.interval.workout.Describe
import java.time.LocalDate

/** Shows what's planned today; tap opens the app. */
class TodayTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val today = LocalDate.now()
        val planned = LibraryStore.get(this).scheduledOn(today)
        val done = Prefs.get(this).isDone(today)

        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("TODAY", 12f, 0xFF98989F.toInt()))
            .addContent(text(planned?.name ?: "Rest day", 20f, 0xFFFF7A45.toInt(), bold = true, maxLines = 2))
            .addContent(text(if (done) "Done ✓" else if (planned != null) "Tap to start" else "Tap to plan", 14f, 0xFFFFFFFF.toInt()))
            .apply { planned?.let { addContent(text(Describe.summary(it.workout, Prefs.get(this@TodayTileService).settings.value.goalSecPerKm), 12f, 0xFFDDDDDD.toInt(), maxLines = 3)) } }
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open)
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(16f)).build())
                    .build(),
            )
            .addContent(column)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(30 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, maxLines: Int = 1) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"

        fun refresh(context: Context) {
            runCatching { getUpdater(context).requestUpdate(TodayTileService::class.java) }
        }
    }
}