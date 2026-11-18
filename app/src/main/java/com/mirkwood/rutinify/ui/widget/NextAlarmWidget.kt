package com.mirkwood.rutinify.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mirkwood.rutinify.MainActivity
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.NextAlarm
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

/** "Lo siguiente" (R-21, D-79): the next pending alarm of the active plans, with "Hecho". */
class NextAlarmWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val next = entry.observeNextAlarm().current()
        val zone = entry.zoneProvider().zone()
        val time = next?.let { NextAlarmText.time(it, LocalDate.now(entry.clock().withZone(zone)), zone).resolve(context.resources) }
        provideContent {
            GlanceTheme { Content(context, next, time) }
        }
    }

    @Composable
    private fun Content(context: Context, next: NextAlarm?, time: String?) {
        val colors = GlanceTheme.colors
        val open = if (next == null) {
            actionStartActivity(Intent(context, MainActivity::class.java))
        } else {
            // R-21.5: the plan with this alarm highlighted, as a notification tap does (R-7.6)
            actionStartActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData("rutinify://order/${next.orderId}?alarm=${next.alarmId}".toUri()),
            )
        }
        Column(
            modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(12.dp).clickable(open),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            if (next == null || time == null) {
                Spacer(GlanceModifier.height(8.dp))
                Text(context.getString(R.string.widget_empty), style = TextStyle(color = colors.onSurface, fontSize = 16.sp))
            } else {
                Text(time, style = TextStyle(color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text(next.title, style = TextStyle(color = colors.onSurface, fontSize = 16.sp), maxLines = 2)
                Text(next.orderName, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.height(8.dp))
                Button(
                    text = context.getString(R.string.notification_action_done),
                    onClick = actionRunCallback<MarkDoneAction>(actionParametersOf(MarkDoneAction.ALARM_ID to next.alarmId)),
                )
            }
        }
    }
}

/** "Hecho" on the widget (R-21.2): R-9.5 for that alarm, then the widget shows the next one. */
class MarkDoneAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val alarmId = parameters[ALARM_ID] ?: return
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).markAlarmDone()(alarmId)
        NextAlarmWidget().updateAll(context)
    }

    companion object {
        val ALARM_ID = ActionParameters.Key<Long>("alarmId")
    }
}

class NextAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlarmWidget()
}
