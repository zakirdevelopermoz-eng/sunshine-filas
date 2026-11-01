package com.example.sunshinesmarttv

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sunshinesmarttv.data.TvApiService
import com.example.sunshinesmarttv.data.TvDashboardState
import com.example.sunshinesmarttv.data.TvTicketItem
import com.example.sunshinesmarttv.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure TV media & notification volume are audible
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            val maxNotif = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0)
        } catch (_: Exception) {
        }

        tts = TextToSpeech(this, this)
        enableEdgeToEdge()
        setContent {
            SunshineSmartTVTheme {
                SmartTvLoungeScreen(
                    onAnnounceTicket = { ticket ->
                        announceCallWithTts(ticket)
                    }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ptLocale = Locale.forLanguageTag("pt-PT")
            val result = tts?.setLanguage(ptLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
            }
            tts?.setSpeechRate(0.92f)
            ttsReady = true
        }
    }

    private fun announceCallWithTts(ticket: TvTicketItem) {
        val spokenCode = ticket.displayCode.replace("-", " ")
        val returnPhrase = if (ticket.isReturnFromExam) ", retorno de exame, " else ", "
        val phrase = "Senha $spokenCode$returnPhrase por favor dirigir-se ao ${ticket.assignedCounterName}."

        // 1. Play hospital notification chime in foreground
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
        } catch (_: Exception) {
        }

        // 2. Synthesize & play Portuguese voice directly inside foreground Activity
        // (avoids Android 16 AudioHardening muting background com.google.android.tts process on emulators)
        lifecycleScope.launch {
            delay(450)
            val playedCloudVoice = withContext(Dispatchers.IO) {
                try {
                    val encoded = URLEncoder.encode(phrase, "UTF-8")
                    val ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=pt-PT&q=$encoded"
                    val mp3Bytes = TvApiService.downloadAudioBytes(ttsUrl)
                    if (mp3Bytes != null && mp3Bytes.isNotEmpty()) {
                        val tempFile = File(cacheDir, "tv_call_voice.mp3")
                        tempFile.writeBytes(mp3Bytes)
                        withContext(Dispatchers.Main) {
                            try {
                                mediaPlayer?.release()
                            } catch (_: Exception) {
                            }
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                                )
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                start()
                            }
                        }
                        true
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }
            }

            if (!playedCloudVoice && ttsReady) {
                tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "call-${ticket.id}-${ticket.callCount}")
            }
        }
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun SmartTvLoungeScreen(
    onAnnounceTicket: (TvTicketItem) -> Unit
) {
    var state by remember {
        mutableStateOf(
            TvDashboardState(
                counters = emptyList(),
                activeCall = null,
                recentCalls = emptyList(),
                waitingTickets = emptyList(),
                connected = false
            )
        )
    }
    var highestCalledAt by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }
    var highlightActive by remember { mutableStateOf(false) }
    var currentTimeText by remember { mutableStateOf("") }

    // Live Clock
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            currentTimeText = sdf.format(Date())
            delay(1000)
        }
    }

    // Real-time Polling from Supabase
    LaunchedEffect(Unit) {
        while (true) {
            val next = TvApiService.fetchDashboardState()
            state = next
            if (next.connected) {
                if (!isInitialized) {
                    // Record initial latest call timestamp so historical calls are never re-announced as ghosts
                    highestCalledAt = next.latestCalledAt
                    isInitialized = true
                } else {
                    val active = next.activeCall
                    if (
                        active != null &&
                        active.status == "CALLED" &&
                        active.calledAt.isNotEmpty() &&
                        active.calledAt > highestCalledAt
                    ) {
                        highestCalledAt = active.calledAt
                        highlightActive = true
                        onAnnounceTicket(active)
                    } else if (next.latestCalledAt > highestCalledAt) {
                        highestCalledAt = next.latestCalledAt
                    }
                }
            }
            if (highlightActive) {
                delay(1400)
                highlightActive = false
            } else {
                delay(1200)
            }
        }
    }

    val cardBorderColor by animateColorAsState(
        targetValue = if (highlightActive) SunshineRed else SunshineBorder,
        animationSpec = tween(450),
        label = "BorderHighlight"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SunshineSurface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Lounge Bar
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SunshineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sunshine_logo),
                            contentDescription = "Sunshine Hospitals",
                            modifier = Modifier
                                .height(34.dp)
                                .widthIn(max = 160.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column {
                            Text(
                                text = "PAINEL DE CHAMADA — SALA DE ESPERA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SunshineGreen,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (state.connected) {
                                    "Sincronizado em Tempo Real com Recepção e Totem POS"
                                } else {
                                    "A aguardar ligação ao servidor (${TvApiService.baseUrl})..."
                                },
                                fontSize = 11.sp,
                                color = SunshineTextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.activeCall?.let { call ->
                            OutlinedButton(
                                onClick = { onAnnounceTicket(call) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Testar Voz TV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = SunshineTextPrimary,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = currentTimeText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Main Split Content
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // LEFT (56%): Active Call Card + Live Counter Strip
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Featured Active Ticket Card
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        border = BorderStroke(if (highlightActive) 3.dp else 1.dp, cardBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val active = state.activeCall
                        if (active == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aguardando próxima chamada da recepção...",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SunshineTextSecondary
                                )
                            }
                        } else {
                            AnimatedContent(
                                targetState = active,
                                transitionSpec = {
                                    (fadeIn(tween(350)) + scaleIn(initialScale = 0.96f, animationSpec = tween(350)))
                                        .togetherWith(fadeOut(tween(200)))
                                },
                                label = "ActiveTicketTvCall"
                            ) { ticket ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(0.58f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                color = SunshineRedContainer,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "CHAMADA ATUAL",
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SunshineRedDark
                                                )
                                            }
                                            if (ticket.isReturnFromExam) {
                                                Surface(
                                                    color = SunshineGreen,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = "RETORNO EXAME",
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = ticket.displayCode,
                                            fontSize = 68.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SunshineRed
                                        )

                                        Text(
                                            text = ticket.subServiceLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SunshineTextSecondary
                                        )
                                    }

                                    // Destination Counter Box
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = SunshineGreen,
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "DIRIJA-SE AO",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SunshineGreenContainer,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = ticket.assignedCounterName,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Chamada #${ticket.callCount}",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Live Counters Strip (A, B, C, D...)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.counters.forEach { counter ->
                            val serving = state.recentCalls.firstOrNull {
                                it.assignedCounterId == counter.id &&
                                    (it.status == "CALLED" || it.status == "IN_SERVICE")
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (serving?.status == "CALLED") SunshineRed else SunshineBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = counter.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SunshineTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Atual:",
                                            fontSize = 10.sp,
                                            color = SunshineTextSecondary
                                        )
                                        Text(
                                            text = serving?.displayCode ?: "Livre",
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (serving != null) SunshineGreen else SunshineTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // RIGHT (44%): Queue Comparison Table (Particulares vs Seguros)
                val particularWaiting = state.waitingTickets.filter { it.category == "PARTICULAR" }
                val seguroWaiting = state.waitingTickets.filter { it.category == "SEGURO" }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SunshineBorder),
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "FILA DE ESPERA — PARTICULARES & SEGUROS",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SunshineTextPrimary,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Compare o tamanho de cada fila em tempo real",
                                    fontSize = 10.sp,
                                    color = SunshineTextSecondary,
                                    maxLines = 1
                                )
                            }

                            Surface(
                                color = Color(0xFFF3EFEF),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "Total: ${particularWaiting.size + seguroWaiting.size} na fila",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SunshineTextPrimary,
                                    maxLines = 1
                                )
                            }
                        }

                        // 2-Column Side-by-Side Table: PARTICULAR vs SEGUROS
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // TABLE COLUMN 1: PARTICULAR
                            QueueCategoryTableColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                title = "PARTICULAR",
                                tickets = particularWaiting,
                                headerColor = Color(0xFFB71C1C),
                                lightTint = Color(0xFFFFF5F5),
                                borderColor = Color(0xFFF5D0D0)
                            )

                            // TABLE COLUMN 2: SEGUROS
                            QueueCategoryTableColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                title = "SEGUROS",
                                tickets = seguroWaiting,
                                headerColor = Color(0xFF1B5E20),
                                lightTint = Color(0xFFF3FAF4),
                                borderColor = Color(0xFFCDE7D0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCategoryTableColumn(
    modifier: Modifier = Modifier,
    title: String,
    tickets: List<TvTicketItem>,
    headerColor: Color,
    lightTint: Color,
    borderColor: Color
) {
    val statusText = when {
        tickets.isEmpty() -> "Sem Espera"
        tickets.size <= 3 -> "Fila Curta"
        tickets.size <= 6 -> "Fila Moderada"
        else -> "Fila Longa"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Table Column Colored Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    color = Color.White,
                    shape = CircleShape
                ) {
                    Text(
                        text = "${tickets.size} na fila",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = headerColor
                    )
                }
            }

            // Table Sub-header (# | SENHA | ESTADO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lightTint)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POS.",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SunshineTextSecondary
                )
                Text(
                    text = "SENHA",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SunshineTextSecondary
                )
                Text(
                    text = "ESTADO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SunshineTextSecondary
                )
            }

            Divider(color = borderColor, thickness = 1.dp)

            // Table Rows
            if (tickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "0 na fila",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = headerColor
                        )
                        Text(
                            text = "Atendimento imediato",
                            fontSize = 10.sp,
                            color = SunshineTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(tickets) { index, item ->
                        val rowBg = if (index % 2 == 0) Color.White else lightTint.copy(alpha = 0.55f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = lightTint,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Text(
                                    text = "${index + 1}º",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = headerColor
                                )
                            }

                            Text(
                                text = item.displayCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SunshineTextPrimary
                            )

                            if (item.isReturnFromExam) {
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "RETORNO",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Aguarda",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SunshineTextSecondary
                                )
                            }
                        }
                        Divider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }

            // Bottom Evaluation Footer (Fila Curta / Moderada / Longa)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lightTint)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Avaliação:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = SunshineTextSecondary
                )
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headerColor
                )
            }
        }
    }
}
