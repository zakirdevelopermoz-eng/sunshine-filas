package com.example.sunshinesmarttv

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val ptLocale = Locale("pt", "PT")
            val result = tts?.setLanguage(ptLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("pt", "BR"))
            }
            tts?.setSpeechRate(0.92f)
            ttsReady = true
        }
    }

    private fun announceCallWithTts(ticket: TvTicketItem) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 380)
        } catch (_: Exception) {
        }

        if (ttsReady) {
            val spokenCode = ticket.displayCode.replace("-", " ")
            val returnPhrase = if (ticket.isReturnFromExam) ", retorno de exame, " else ", "
            val phrase = "Senha $spokenCode$returnPhrase por favor dirigir-se ao ${ticket.assignedCounterName}."
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "call-${ticket.id}-${ticket.callCount}")
        }
    }

    override fun onDestroy() {
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
    var lastCallSignature by remember { mutableStateOf("") }
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

    // Real-time Polling from Server (10.0.2.2:5173/api/state)
    LaunchedEffect(Unit) {
        while (true) {
            val next = TvApiService.fetchDashboardState()
            state = next
            val active = next.activeCall
            if (active != null) {
                val sig = "${active.id}-${active.callCount}-${active.calledAt}"
                if (lastCallSignature.isNotEmpty() && lastCallSignature != sig) {
                    highlightActive = true
                    onAnnounceTicket(active)
                }
                lastCallSignature = sig
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
                // LEFT (62%): Active Call Card + Live Counter Strip
                Column(
                    modifier = Modifier
                        .weight(0.62f)
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

                // RIGHT (38%): Recent Calls & Waiting Queue
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Recent Calls
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SunshineBorder),
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ÚLTIMAS SENHAS CHAMADAS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SunshineTextSecondary,
                                letterSpacing = 0.8.sp
                            )

                            state.recentCalls.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SunshineSurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = item.displayCode,
                                                fontSize = 18.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SunshineTextPrimary
                                            )
                                            Text(
                                                text = item.subServiceLabel,
                                                fontSize = 11.sp,
                                                color = SunshineTextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                        Surface(
                                            color = SunshineTextPrimary,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = item.assignedCounterName,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Next in Waiting Queue
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SunshineBorder),
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PRÓXIMAS NA FILA DE ESPERA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SunshineTextSecondary,
                                letterSpacing = 0.8.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.waitingTickets.take(4).forEach { w ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (w.isReturnFromExam) Color(0xFFE8F5E9) else SunshineSurface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (w.isReturnFromExam) SunshineGreen else SunshineBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = w.displayCode,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = SunshineTextPrimary
                                            )
                                            Text(
                                                text = if (w.isReturnFromExam) "Retorno" else w.category.take(6),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (w.isReturnFromExam) SunshineGreen else SunshineTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
