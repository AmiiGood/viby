package com.sweetcode.viby.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Envuelve el reproductor para que "siguiente/anterior" SIEMPRE estén disponibles
 * (incluso en la notificación y la pantalla bloqueada) y den la vuelta a la cola
 * al llegar al final/inicio, en vez de quedarse atascados.
 */
@UnstableApi
class WrappingForwardingPlayer(player: Player) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun seekToNext() = goNext()
    override fun seekToNextMediaItem() = goNext()

    override fun seekToPrevious() = goPrevious()
    override fun seekToPreviousMediaItem() = goPrevious()

    private fun goNext() {
        if (hasNextMediaItem()) super.seekToNextMediaItem()
        else if (mediaItemCount > 0) seekToDefaultPosition(0) // vuelve al inicio
    }

    private fun goPrevious() {
        if (hasPreviousMediaItem()) super.seekToPreviousMediaItem()
        else if (mediaItemCount > 0) seekToDefaultPosition(mediaItemCount - 1) // salta al final
    }
}
