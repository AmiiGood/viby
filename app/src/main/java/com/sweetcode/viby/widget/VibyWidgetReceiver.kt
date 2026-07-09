package com.sweetcode.viby.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Receiver que Android usa para dibujar/actualizar el widget de Viby. */
class VibyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VibyWidget()
}
