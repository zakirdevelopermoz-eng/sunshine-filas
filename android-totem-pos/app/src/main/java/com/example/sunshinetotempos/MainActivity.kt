package com.example.sunshinetotempos

import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sunshinetotempos.data.PosApiService
import com.example.sunshinetotempos.data.TicketItem
import com.example.sunshinetotempos.theme.*
import kotlinx.coroutines.launch

enum class TotemScreenStep {
    HOME,
    OUTROS_MODE,
    REUSE_TICKET,
    TICKET_PRINTED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            SunshineTotemPOSTheme {
                TotemPosKioskApp()
            }
        }
    }
}

private fun playConfirmationBeep() {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 220)
    } catch (_: Exception) {
    }
}

@Composable
fun TotemPosKioskApp() {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(TotemScreenStep.HOME) }
    var printedTicket by remember { mutableStateOf<TicketItem?>(null) }
    var isReusedConfirmation by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    var reuseInput by remember { mutableStateOf("S-001") }
    var reuseError by remember { mutableStateOf<String?>(null) }
    var examHoldTickets by remember { mutableStateOf<List<TicketItem>>(emptyList()) }

    LaunchedEffect(step) {
        if (step == TotemScreenStep.HOME || step == TotemScreenStep.REUSE_TICKET) {
            examHoldTickets = PosApiService.fetchExamHoldTickets()
            if (examHoldTickets.isNotEmpty() && reuseInput.isEmpty()) {
                reuseInput = examHoldTickets.first().displayCode
            }
        }
    }

    fun issueNewTicket(category: String, subService: String, label: String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            val created = PosApiService.createTicket(category, subService, label)
            isBusy = false
            if (created != null) {
                PosApiService.buildEscPosReceiptBytes(created)
                playConfirmationBeep()
                printedTicket = created
                isReusedConfirmation = false
                step = TotemScreenStep.TICKET_PRINTED
            }
        }
    }

    fun submitReuseTicket(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            reuseError = "Selecione ou digite o código da sua senha (ex: S-001)."
            return
        }
        if (isBusy) return
        isBusy = true
        reuseError = null
        scope.launch {
            val res = PosApiService.reuseTicket(clean)
            isBusy = false
            if (res.ok && res.ticket != null) {
                playConfirmationBeep()
                printedTicket = res.ticket
                isReusedConfirmation = true
                step = TotemScreenStep.TICKET_PRINTED
            } else {
                reuseError = res.message
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6F6))
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Minimalist White Top Header Bar matching reference image
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFEAE3E3)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sunshine_logo),
                        contentDescription = "Sunshine Hospitals",
                        modifier = Modifier
                            .height(34.dp)
                            .widthIn(max = 190.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Dark Return / Secondary Menu Button at Top-Right
                    Surface(
                        onClick = {
                            reuseError = null
                            step = if (step == TotemScreenStep.HOME) {
                                TotemScreenStep.OUTROS_MODE
                            } else {
                                TotemScreenStep.HOME
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1F1D1D),
                        modifier = Modifier
                            .width(48.dp)
                            .height(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UndoReturnArrowIcon(tint = Color.White, size = 20.dp)
                        }
                    }
                }
            }

            // 2. Main Body Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.98f, animationSpec = tween(260)))
                            .togetherWith(fadeOut(animationSpec = tween(180)))
                    },
                    label = "KioskMainStep"
                ) { currentStep ->
                    when (currentStep) {
                        // MAIN SCREEN: Exact match to user reference image
                        TotemScreenStep.HOME -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Toque na opção desejada para tirar sua senha:",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1B1919),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Two Giant High-Contrast Cards Side-by-Side
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // LEFT CARD: PAGAMENTO PARTICULAR (Sem Seguro)
                                    Surface(
                                        onClick = {
                                            issueNewTicket(
                                                category = "PARTICULAR",
                                                subService = "CONSULTA_PARTICULAR",
                                                label = "Marcação de Consulta (Particular)"
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFFB71515),
                                        shadowElevation = 5.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "CONSULTA OU EXAME HOJE (VOU PAGAR\nEM DINHEIRO, CARTÃO OU M-PESA)",
                                                fontSize = 18.sp,
                                                lineHeight = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )

                                            ParticularCoinPlusIllustration(size = 190.dp)

                                            Text(
                                                text = "PAGAMENTO PARTICULAR\n(Sem Seguro)",
                                                fontSize = 35.sp,
                                                lineHeight = 41.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )

                                            Text(
                                                text = "Consultas e exames sem convênio.",
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.95f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // RIGHT CARD: TENHO SEGURO DE SAÚDE / CONVÊNIO
                                    Surface(
                                        onClick = {
                                            issueNewTicket(
                                                category = "SEGURO",
                                                subService = "CONSULTA_SEGURO",
                                                label = "Marcação / Faturação com Seguro"
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF2D6A30),
                                        shadowElevation = 5.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp, vertical = 22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "CONSULTA OU EXAME HOJE (TENHO\nCONVÊNIO OU SEGURO)",
                                                fontSize = 18.sp,
                                                lineHeight = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )

                                            SeguroShieldCardIllustration(size = 190.dp)

                                            Text(
                                                text = "TENHO SEGURO DE\nSAÚDE / CONVÊNIO",
                                                fontSize = 35.sp,
                                                lineHeight = 41.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )

                                            Text(
                                                text = "Atendimento por planos de saúde e parcerias.",
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.95f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Bottom Full-Width Banner: Reativar Senha Pós-Exame
                                Surface(
                                    onClick = { step = TotemScreenStep.REUSE_TICKET },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Transparent,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFF15534C),
                                                        Color(0xFF0F385A)
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(
                                                    SpanStyle(
                                                        fontWeight = FontWeight.Normal,
                                                        color = Color.White
                                                    )
                                                ) {
                                                    append("Já foi atendido hoje e voltou de exames? ")
                                                }
                                                withStyle(
                                                    SpanStyle(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                ) {
                                                    append("TOQUE AQUI PARA REATIVAR SUA SENHA")
                                                }
                                            },
                                            fontSize = 21.sp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        UndoReturnArrowIcon(tint = Color.White, size = 25.dp)
                                    }
                                }
                            }
                        }

                        // SECONDARY SCREEN: OUTROS SERVIÇOS (Cotação, Agendamento, Outros) with Giant Buttons & Large Text
                        TotemScreenStep.OUTROS_MODE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Outros Serviços e Orçamentos — Toque na opção desejada:",
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1B1919),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    GiantSolidServiceCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        topTag = "ORÇAMENTO DE EXAMES OU CONSULTAS",
                                        title = "COTAÇÃO\nSIMPLES",
                                        subtitle = "Consulta rápida de preços no Balcão D.",
                                        backgroundColor = Color(0xFF1E3A5F),
                                        badgeCircleColor = Color(0xFFD0E2F5),
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "COTACAO_SIMPLES",
                                                label = "Cotação Simples"
                                            )
                                        }
                                    )

                                    GiantSolidServiceCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        topTag = "MARCAÇÃO PARA DATAS FUTURAS",
                                        title = "AGENDAMENTO\nDE CONSULTA",
                                        subtitle = "Programar consultas e exames futuros.",
                                        backgroundColor = Color(0xFF2D6A30),
                                        badgeCircleColor = Color(0xFFCFECCF),
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "AGENDAMENTO_CONSULTA",
                                                label = "Agendamento de Consulta"
                                            )
                                        }
                                    )

                                    GiantSolidServiceCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        topTag = "LEVANTAMENTO DE EXAMES E APOIO",
                                        title = "OUTROS\nSERVIÇOS",
                                        subtitle = "Relatórios médicos e informações gerais.",
                                        backgroundColor = Color(0xFFB71515),
                                        badgeCircleColor = Color(0xFFFAD2D2),
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "OUTROS_SERVICOS",
                                                label = "Outros Serviços & Informações"
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Surface(
                                    onClick = { step = TotemScreenStep.HOME },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1F1D1D),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "VOLTAR PARA CONSULTAS PARTICULAR OU SEGURO",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        UndoReturnArrowIcon(tint = Color.White, size = 22.dp)
                                    }
                                }
                            }
                        }

                        // REUSE TICKET AFTER EXAMS (Large Typography & Giant Tactile Keypad)
                        TotemScreenStep.REUSE_TICKET -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // Left Column: Big Instructions + Large Quick-Select Ticket Buttons
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF15534C),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .weight(0.46f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(22.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = "RETORNO DE EXAMES",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFA7F3D0),
                                                letterSpacing = 1.sp
                                            )

                                            Text(
                                                text = "Reativar a sua Senha com Prioridade",
                                                fontSize = 28.sp,
                                                lineHeight = 33.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )

                                            Text(
                                                text = "Toque na sua senha abaixo ou digite o número no teclado ao lado para voltar à fila sem perder a vez.",
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp,
                                                color = Color.White.copy(alpha = 0.92f)
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (examHoldTickets.isNotEmpty()) {
                                                Text(
                                                    text = "TOQUE NA SUA SENHA:",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFA7F3D0)
                                                )
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    items(examHoldTickets) { item ->
                                                        Surface(
                                                            onClick = { submitReuseTicket(item.displayCode) },
                                                            shape = RoundedCornerShape(14.dp),
                                                            color = Color.White
                                                        ) {
                                                            Text(
                                                                text = item.displayCode,
                                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 22.sp,
                                                                color = Color(0xFF15534C)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        reuseError?.let { err ->
                                            Surface(
                                                color = Color(0xFFB71515),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = err,
                                                    modifier = Modifier.padding(14.dp),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                reuseError = null
                                                step = TotemScreenStep.HOME
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color(0xFF15534C)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                        ) {
                                            Text(
                                                text = "VOLTAR AO ECRÃ INICIAL",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                // Right Column: Large Touch Keypad
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE5DFDE)),
                                    modifier = Modifier
                                        .weight(0.54f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color(0xFFFAF6F6),
                                                border = BorderStroke(2.5.dp, Color(0xFF2D6A30)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = if (reuseInput.isEmpty()) "S-001" else reuseInput,
                                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (reuseInput.isEmpty()) Color(0xFFB8AEAD) else Color(0xFF1B1919)
                                                )
                                            }

                                            listOf("P-", "S-", "G-").forEach { pref ->
                                                val selected = reuseInput.startsWith(pref)
                                                Surface(
                                                    onClick = {
                                                        reuseError = null
                                                        reuseInput = pref
                                                    },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (selected) Color(0xFFB71515) else Color(0xFFEDE7E7)
                                                ) {
                                                    Text(
                                                        text = pref,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 20.sp,
                                                        color = if (selected) Color.White else Color(0xFF1B1919)
                                                    )
                                                }
                                            }
                                        }

                                        val rows = listOf(
                                            listOf("1", "2", "3", "4"),
                                            listOf("5", "6", "7", "8"),
                                            listOf("9", "0", "LIMPAR", "APAGAR")
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 8.dp)
                                        ) {
                                            rows.forEach { r ->
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    r.forEach { key ->
                                                        Surface(
                                                            onClick = {
                                                                reuseError = null
                                                                when (key) {
                                                                    "LIMPAR" -> reuseInput = ""
                                                                    "APAGAR" -> if (reuseInput.isNotEmpty()) {
                                                                        reuseInput = reuseInput.dropLast(1)
                                                                    }
                                                                    else -> if (reuseInput.length < 6) {
                                                                        reuseInput += key
                                                                    }
                                                                }
                                                            },
                                                            shape = RoundedCornerShape(14.dp),
                                                            color = Color(0xFFF3EFEF),
                                                            border = BorderStroke(1.dp, Color(0xFFDDD6D5)),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = key,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = if (key.length > 1) 15.sp else 24.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = Color(0xFF1B1919)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = { submitReuseTicket(reuseInput) },
                                            enabled = !isBusy,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6A30)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                        ) {
                                            Text(
                                                text = "CONFIRMAR E REATIVAR MINHA SENHA",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // PRINTED TICKET SCREEN (Large Text & 80mm Receipt)
                        TotemScreenStep.TICKET_PRINTED -> {
                            val ticket = printedTicket
                            if (ticket != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color(0xFF2D6A30),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .weight(0.54f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(26.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                Text(
                                                    text = if (isReusedConfirmation) {
                                                        "SENHA REATIVADA COM PRIORIDADE"
                                                    } else {
                                                        "SENHA EMITIDA COM SUCESSO"
                                                    },
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFCFECCF)
                                                )

                                                Text(
                                                    text = if (isReusedConfirmation) {
                                                        "A sua senha voltou à fila de atendimento!"
                                                    } else {
                                                        "Retire a sua senha impressa abaixo"
                                                    },
                                                    fontSize = 32.sp,
                                                    lineHeight = 38.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )

                                                Text(
                                                    text = "Aguarde na sala de espera. O seu número será chamado na Smart TV com aviso de voz.",
                                                    fontSize = 18.sp,
                                                    lineHeight = 25.sp,
                                                    color = Color.White.copy(alpha = 0.95f)
                                                )
                                            }

                                            Button(
                                                onClick = { step = TotemScreenStep.HOME },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF1B1919)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(58.dp)
                                            ) {
                                                Text(
                                                    text = "CONCLUIR E VOLTAR AO INÍCIO",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.White,
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.5.dp, Color(0xFFDCD4D3)),
                                        modifier = Modifier
                                            .weight(0.46f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(
                                                    painter = painterResource(id = R.drawable.sunshine_logo),
                                                    contentDescription = "Sunshine Hospitals",
                                                    modifier = Modifier.height(34.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "TALÃO DE ATENDIMENTO POS 80MM",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SunshineTextSecondary
                                                )
                                            }

                                            DashedReceiptDivider()

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = ticket.subServiceLabel,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SunshineTextPrimary,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = ticket.displayCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 62.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFB71515)
                                                )
                                                if (ticket.isReturnFromExam) {
                                                    Surface(
                                                        color = Color(0xFFCFECCF),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "PRIORIDADE PÓS-EXAME",
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color(0xFF2D6A30)
                                                        )
                                                    }
                                                }
                                            }

                                            DashedReceiptDivider()

                                            Text(
                                                text = "Guarde este talão caso precise voltar após exames.",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = SunshineTextSecondary,
                                                textAlign = TextAlign.Center
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

/**
 * Custom Vector Illustration for Left Red Card:
 * Large light-pink circle with Dollar Coin ($) and overlapping Medical Cross (+) circle at bottom-right.
 */
@Composable
private fun ParticularCoinPlusIllustration(size: Dp) {
    val redColor = Color(0xFFB71515)
    val pinkFill = Color(0xFFFAD2D2)

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Main large light-pink circle
        val mainRadius = w * 0.44f
        val mainCenter = Offset(w * 0.46f, h * 0.47f)
        drawCircle(
            color = pinkFill,
            radius = mainRadius,
            center = mainCenter
        )

        // 2. Inner dark-red coin ring inside main circle
        drawCircle(
            color = redColor,
            radius = mainRadius * 0.72f,
            center = mainCenter,
            style = Stroke(width = 5.5.dp.toPx())
        )

        // 3. Bold "$" symbol centered in coin ring
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(183, 21, 21)
                textSize = w * 0.42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = paint.fontMetrics
            val textY = mainCenter.y - (fontMetrics.ascent + fontMetrics.descent) / 2f
            drawText("$", mainCenter.x, textY, paint)
        }

        // 4. Overlapping bottom-right badge circle with '+'
        val subRadius = w * 0.23f
        val subCenter = Offset(w * 0.75f, h * 0.71f)

        // Outer red separator border
        drawCircle(
            color = redColor,
            radius = subRadius + 4.dp.toPx(),
            center = subCenter
        )
        // Light pink fill
        drawCircle(
            color = pinkFill,
            radius = subRadius,
            center = subCenter
        )
        // Plus sign '+'
        val arm = subRadius * 0.52f
        val plusStroke = 5.5.dp.toPx()
        drawLine(
            color = redColor,
            start = Offset(subCenter.x - arm, subCenter.y),
            end = Offset(subCenter.x + arm, subCenter.y),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = redColor,
            start = Offset(subCenter.x, subCenter.y - arm),
            end = Offset(subCenter.x, subCenter.y + arm),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Custom Vector Illustration for Right Green Card:
 * Large light-mint circle with Medical Shield (+) and overlapping Insurance/Health Card at bottom-right.
 */
@Composable
private fun SeguroShieldCardIllustration(size: Dp) {
    val greenColor = Color(0xFF2D6A30)
    val mintFill = Color(0xFFCFECCF)

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 1. Main large light-mint circle
        drawCircle(
            color = mintFill,
            radius = w * 0.47f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // 2. Shield outline inside circle (slightly left-top of center)
        val sx = w * 0.45f
        val sy = h * 0.19f
        val sw = w * 0.44f
        val sh = h * 0.56f
        val shieldPath = Path().apply {
            moveTo(sx, sy)
            lineTo(sx + sw * 0.5f, sy + sh * 0.14f)
            lineTo(sx + sw * 0.5f, sy + sh * 0.52f)
            quadraticTo(sx + sw * 0.5f, sy + sh * 0.84f, sx, sy + sh)
            quadraticTo(sx - sw * 0.5f, sy + sh * 0.84f, sx - sw * 0.5f, sy + sh * 0.52f)
            lineTo(sx - sw * 0.5f, sy + sh * 0.14f)
            close()
        }
        drawPath(
            path = shieldPath,
            color = greenColor,
            style = Stroke(width = 5.5.dp.toPx(), join = StrokeJoin.Round)
        )

        // 3. Medical Cross '+' inside shield
        val crossCenterY = sy + sh * 0.47f
        val crossArm = sw * 0.21f
        val crossStroke = 5.5.dp.toPx()
        drawLine(
            color = greenColor,
            start = Offset(sx - crossArm, crossCenterY),
            end = Offset(sx + crossArm, crossCenterY),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = greenColor,
            start = Offset(sx, crossCenterY - crossArm),
            end = Offset(sx, crossCenterY + crossArm),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round
        )

        // 4. Overlapping Health/Insurance Card at bottom-right
        val cardLeft = w * 0.48f
        val cardTop = h * 0.54f
        val cardW = w * 0.34f
        val cardH = h * 0.23f

        // Mint halo around card
        drawRoundRect(
            color = mintFill,
            topLeft = Offset(cardLeft - 3.dp.toPx(), cardTop - 3.dp.toPx()),
            size = Size(cardW + 6.dp.toPx(), cardH + 6.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        // Green card body
        drawRoundRect(
            color = greenColor,
            topLeft = Offset(cardLeft, cardTop),
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        // Small '+' on the left of the card
        val cPlusX = cardLeft + cardW * 0.25f
        val cPlusY = cardTop + cardH * 0.45f
        val cArm = cardH * 0.18f
        drawLine(
            color = mintFill,
            start = Offset(cPlusX - cArm, cPlusY),
            end = Offset(cPlusX + cArm, cPlusY),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = mintFill,
            start = Offset(cPlusX, cPlusY - cArm),
            end = Offset(cPlusX, cPlusY + cArm),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Horizontal card lines at bottom/right
        drawLine(
            color = mintFill,
            start = Offset(cardLeft + cardW * 0.14f, cardTop + cardH * 0.78f),
            end = Offset(cardLeft + cardW * 0.86f, cardTop + cardH * 0.78f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun GiantSolidServiceCard(
    modifier: Modifier = Modifier,
    topTag: String,
    title: String,
    subtitle: String,
    backgroundColor: Color,
    badgeCircleColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        shadowElevation = 5.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = topTag,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(badgeCircleColor),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(54.dp)) {
                    val w = size.width
                    val h = size.height
                    drawCircle(
                        color = backgroundColor,
                        radius = w * 0.45f,
                        style = Stroke(width = 4.5.dp.toPx())
                    )
                    drawLine(
                        color = backgroundColor,
                        start = Offset(w * 0.5f, h * 0.25f),
                        end = Offset(w * 0.5f, h * 0.75f),
                        strokeWidth = 4.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = backgroundColor,
                        start = Offset(w * 0.25f, h * 0.5f),
                        end = Offset(w * 0.75f, h * 0.5f),
                        strokeWidth = 4.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = title,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UndoReturnArrowIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 2.4.dp.toPx()

        // Left-pointing arrow head at top-left
        drawLine(
            color = tint,
            start = Offset(w * 0.42f, h * 0.20f),
            end = Offset(w * 0.20f, h * 0.38f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.38f),
            end = Offset(w * 0.42f, h * 0.56f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Horizontal top bar + right U-turn curve
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.38f)
            lineTo(w * 0.62f, h * 0.38f)
            quadraticTo(w * 0.84f, h * 0.38f, w * 0.84f, h * 0.60f)
            quadraticTo(w * 0.84f, h * 0.82f, w * 0.62f, h * 0.82f)
            lineTo(w * 0.30f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun DashedReceiptDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = Color(0xFFD5CDCC),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    }
}
