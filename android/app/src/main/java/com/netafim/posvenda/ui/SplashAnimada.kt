package com.netafim.posvenda.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.netafim.posvenda.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

private const val PROPORCAO_VIDEO = 9f / 16f
private const val TEMPO_LIMITE_MS = 12_000L
private const val COR_FUNDO_SPLASH = 0xFF011147

/** Exibe a animação oficial enquanto o estado inicial do aplicativo é restaurado. */
@Composable
internal fun SplashAnimada(aoConcluir: () -> Unit) {
    val contexto = LocalContext.current
    val aoConcluirAtual by rememberUpdatedState(aoConcluir)
    val jaConcluida = remember { AtomicBoolean(false) }
    val concluir = remember {
        {
            if (jaConcluida.compareAndSet(false, true)) {
                aoConcluirAtual()
            }
        }
    }
    val video = remember(contexto) {
        VideoView(contexto).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setOnPreparedListener { player ->
                player.isLooping = false
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                start()
            }
            setOnCompletionListener { concluir() }
            setOnErrorListener { _, _, _ ->
                concluir()
                true
            }
            setVideoURI(
                Uri.parse("android.resource://${contexto.packageName}/${R.raw.splash_screen_logo_animation}"),
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(TEMPO_LIMITE_MS)
        concluir()
    }
    DisposableEffect(video) {
        onDispose {
            video.setOnPreparedListener(null)
            video.setOnCompletionListener(null)
            video.setOnErrorListener(null)
            video.stopPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(COR_FUNDO_SPLASH)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { video },
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(PROPORCAO_VIDEO),
        )
    }
}

/** Mantém os ícones legíveis no fundo escuro da splash e os restaura nas telas claras. */
@Composable
internal fun AparenciaBarrasDoSistema(fundoEscuro: Boolean) {
    val atividade = LocalContext.current.encontrarAtividade() ?: return
    SideEffect {
        WindowCompat.getInsetsController(atividade.window, atividade.window.decorView).apply {
            isAppearanceLightStatusBars = !fundoEscuro
            isAppearanceLightNavigationBars = !fundoEscuro
        }
    }
}

private tailrec fun Context.encontrarAtividade(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarAtividade()
    else -> null
}
