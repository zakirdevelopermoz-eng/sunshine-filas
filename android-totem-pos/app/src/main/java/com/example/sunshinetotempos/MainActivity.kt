package com.example.sunshinetotempos

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sunshinetotempos.data.PosApiService
import com.example.sunshinetotempos.data.TicketItem
import com.example.sunshinetotempos.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TotemScreenStep {
    HOME,
    CONSULTA_MODE,
    OUTROS_MODE,
    REUSE_TICKET,
    TICKET_PRINTED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Immersive Kiosk Mode for Tablet POS
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

    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(PosApiService.baseUrl) }
    var clockText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clockText = sdf.format(Date())
            delay(10000)
        }
    }

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFCF9F9), Color(0xFFF2ECEB))
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Sleek Hospital Kiosk Header
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFE6DFDE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sunshine_logo),
                            contentDescription = "Sunshine Hospitals",
                            modifier = Modifier
                                .height(36.dp)
                                .widthIn(max = 165.dp),
                            contentScale = ContentScale.Fit
                        )
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .width(1.dp)
                                .background(Color(0xFFE5DFDE))
                        )
                        Column {
                            Text(
                                text = "TERMINAL DE AUTO-ATENDIMENTO & SENHAS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SunshineRed,
                                letterSpacing = 1.1.sp
                            )
                            Text(
                                text = "Recepção Central · Toque na opção desejada",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SunshineTextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (step != TotemScreenStep.HOME) {
                            Button(
                                onClick = {
                                    step = TotemScreenStep.HOME
                                    reuseError = null
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1C1B1B),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Voltar ao Início",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                onClick = { showServerDialog = true },
                                shape = CircleShape,
                                color = Color(0xFFF6F2F2),
                                border = BorderStroke(1.dp, Color(0xFFE5DFDE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(SunshineGreen)
                                    )
                                    Text(
                                        text = "POS Online",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SunshineTextPrimary
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1C1B1B)
                        ) {
                            Text(
                                text = clockText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Center Main Content (Zero-scroll Bento Layout that fits 100% of Tablet Landscape)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.98f, animationSpec = tween(300)))
                            .togetherWith(fadeOut(animationSpec = tween(200)))
                    },
                    label = "KioskMainStep"
                ) { currentStep ->
                    when (currentStep) {
                        // STEP 1: HOME SCREEN (2 Side-by-Side Hero Cards + Bottom Exam Return Bar)
                        TotemScreenStep.HOME -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Top 2 Primary Action Cards Side-by-Side
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // LEFT CARD: Marcar Consulta (Seguro ou Particular)
                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "BALCÕES A, B, C",
                                        categoryTag = "ATENDIMENTO MÉDICO & FATURAÇÃO",
                                        title = "Marcar Consulta\n(Seguro ou Particular)",
                                        description = "Abertura de ficha, elegibilidade de seguradoras, guias médicas e atendimento particular.",
                                        ctaText = "Selecionar Seguro ou Particular",
                                        accentColor = SunshineRed,
                                        containerTint = Color(0xFFFFDAD6),
                                        iconType = "MEDICAL",
                                        onClick = { step = TotemScreenStep.CONSULTA_MODE }
                                    )

                                    // RIGHT CARD: Cotação, Agendamento & Outros
                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "BALCÃO D",
                                        categoryTag = "ORÇAMENTOS & MARCAÇÕES FUTURAS",
                                        title = "Cotação Simples,\nAgendamento & Outros",
                                        description = "Orçamentos rápidos de exames/consultas, marcações para datas futuras e informações gerais.",
                                        ctaText = "Escolher Serviço Geral",
                                        accentColor = SunshineGreen,
                                        containerTint = Color(0xFFC8E6C9),
                                        iconType = "CALENDAR",
                                        onClick = { step = TotemScreenStep.OUTROS_MODE }
                                    )
                                }

                                // Bottom Docked Bar: Reutilizar Senha (Retorno de Exames)
                                Surface(
                                    onClick = { step = TotemScreenStep.REUSE_TICKET },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp,
                                    border = BorderStroke(1.5.dp, SunshineGreen.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFFE8F5E9),
                                                        Color(0xFFFFFFFF)
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 22.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(SunshineGreen),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                KioskVectorIcon(type = "RETURN", tint = Color.White, size = 22.dp)
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Já foi atendido e regressou de Exames? Reutilizar Senha",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = SunshineTextPrimary
                                                    )
                                                    Surface(
                                                        color = SunshineGreen,
                                                        shape = CircleShape
                                                    ) {
                                                        Text(
                                                            text = "PRIORIDADE DE RETORNO",
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Volte à fila da recepção com o código do seu talão sem ter de ficar para trás.",
                                                    fontSize = 12.sp,
                                                    color = SunshineTextSecondary
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = SunshineGreen
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Reativar Senha",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                KioskVectorIcon(type = "ARROW_RIGHT", tint = Color.White, size = 16.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // STEP 2A: MARCAR CONSULTA (PARTICULAR VS SEGURO SIDE-BY-SIDE)
                        TotemScreenStep.CONSULTA_MODE -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "PASSO 2 DE 2 — MODALIDADE DE ATENDIMENTO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SunshineRed,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Qual é a modalidade da sua consulta?",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SunshineTextPrimary
                                        )
                                    }
                                    Text(
                                        text = "Toque num cartão para imprimir a senha imediatamente",
                                        fontSize = 12.sp,
                                        color = SunshineTextSecondary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    // Particular Card
                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "SÉRIE P · BALCÃO A",
                                        categoryTag = "ATENDIMENTO PARTICULAR",
                                        title = "Paciente Particular\n(Sem Seguro)",
                                        description = "Atendimento prioritário no Balcão A para consultas particulares, exames e pagamentos diretos.",
                                        ctaText = "Imprimir Senha Particular",
                                        accentColor = SunshineRed,
                                        containerTint = Color(0xFFFFDAD6),
                                        iconType = "MEDICAL",
                                        onClick = {
                                            issueNewTicket(
                                                category = "PARTICULAR",
                                                subService = "CONSULTA_PARTICULAR",
                                                label = "Marcação de Consulta (Particular)"
                                            )
                                        }
                                    )

                                    // Seguro Card
                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "SÉRIE S · BALCÕES B e C",
                                        categoryTag = "SEGURADORAS & CONVÉNIOS",
                                        title = "Paciente com Seguro\nde Saúde",
                                        description = "Atendimento nos Balcões B e C (com apoio do Balcão A) para validação e faturação de seguros.",
                                        ctaText = "Imprimir Senha Seguro",
                                        accentColor = SunshineGreen,
                                        containerTint = Color(0xFFC8E6C9),
                                        iconType = "SHIELD",
                                        onClick = {
                                            issueNewTicket(
                                                category = "SEGURO",
                                                subService = "CONSULTA_SEGURO",
                                                label = "Marcação / Faturação com Seguro"
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // STEP 2B: OUTROS SERVIÇOS (3 SIDE-BY-SIDE CARDS FOR BALCÃO D)
                        TotemScreenStep.OUTROS_MODE -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "PASSO 2 DE 2 — OUTROS SERVIÇOS DE RECEPÇÃO (BALCÃO D)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SunshineGreen,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Selecione o serviço que pretende realizar",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SunshineTextPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "SÉRIE G · BALCÃO D",
                                        categoryTag = "ORÇAMENTO RÁPIDO",
                                        title = "Cotação\nSimples",
                                        description = "Consulta rápida de preços e orçamentos para exames, análises clínicas ou procedimentos médicos.",
                                        ctaText = "Imprimir Senha Cotação",
                                        accentColor = Color(0xFF1E293B),
                                        containerTint = Color(0xFFE2E8F0),
                                        iconType = "RECEIPT",
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "COTACAO_SIMPLES",
                                                label = "Cotação Simples"
                                            )
                                        }
                                    )

                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "SÉRIE G · BALCÃO D",
                                        categoryTag = "MARCAÇÃO PROGRAMADA",
                                        title = "Agendamento\nde Consulta",
                                        description = "Programação de consultas de especialidade ou exames complementares para datas futuras.",
                                        ctaText = "Imprimir Senha Agendamento",
                                        accentColor = SunshineGreen,
                                        containerTint = Color(0xFFC8E6C9),
                                        iconType = "CALENDAR",
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "AGENDAMENTO_CONSULTA",
                                                label = "Agendamento de Consulta"
                                            )
                                        }
                                    )

                                    KioskPrimaryCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        badgeText = "SÉRIE G · BALCÃO D",
                                        categoryTag = "APOIO GERAL",
                                        title = "Outros Serviços\n& Informações",
                                        description = "Levantamento de relatórios médicos, segunda via de documentos ou esclarecimento de dúvidas.",
                                        ctaText = "Imprimir Senha Geral",
                                        accentColor = SunshineRed,
                                        containerTint = Color(0xFFFFDAD6),
                                        iconType = "MEDICAL",
                                        onClick = {
                                            issueNewTicket(
                                                category = "OUTROS",
                                                subService = "OUTROS_SERVICOS",
                                                label = "Outros Serviços & Informações"
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // STEP 2C: REUSE TICKET AFTER EXAMS (2-COLUMN SPLIT WITH BUILT-IN TOUCH KEYPAD)
                        TotemScreenStep.REUSE_TICKET -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // Left Column: Instructions + Quick Select Chips
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, SunshineBorder),
                                    modifier = Modifier
                                        .weight(0.48f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(22.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Surface(
                                                color = SunshineGreenContainer,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "PRIORIDADE PÓS-EXAME",
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SunshineGreen
                                                )
                                            }

                                            Text(
                                                text = "Reutilizar a sua Senha na Fila",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SunshineTextPrimary
                                            )

                                            Text(
                                                text = "Se já passou pela recepção, realizou os seus exames e precisa regressar ao balcão, indique o número da sua senha para voltar à fila sem perder a sua vez.",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = SunshineTextSecondary
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "SENHAS EM EXAME / AGUARDANDO RETORNO:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SunshineTextSecondary,
                                                letterSpacing = 0.8.sp
                                            )

                                            if (examHoldTickets.isEmpty()) {
                                                Text(
                                                    text = "Use o teclado ao lado para digitar a sua senha.",
                                                    fontSize = 12.sp,
                                                    color = SunshineTextSecondary
                                                )
                                            } else {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(examHoldTickets) { item ->
                                                        Surface(
                                                            onClick = { submitReuseTicket(item.displayCode) },
                                                            shape = RoundedCornerShape(14.dp),
                                                            color = Color(0xFFE8F5E9),
                                                            border = BorderStroke(1.5.dp, SunshineGreen)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = item.displayCode,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    fontSize = 15.sp,
                                                                    color = SunshineGreen
                                                                )
                                                                Text(
                                                                    text = "Reativar",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = SunshineTextPrimary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        reuseError?.let { err ->
                                            Surface(
                                                color = SunshineRedContainer,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = err,
                                                    modifier = Modifier.padding(12.dp),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SunshineRedDark
                                                )
                                            }
                                        }
                                    }
                                }

                                // Right Column: Built-in Tactile Keypad (No ugly system keyboard popup!)
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, SunshineBorder),
                                    modifier = Modifier
                                        .weight(0.52f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Code Display Box + Series Prefixes
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color(0xFFFCF9F9),
                                                border = BorderStroke(2.dp, SunshineGreen),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = if (reuseInput.isEmpty()) "S-001" else reuseInput,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (reuseInput.isEmpty()) Color(0xFFB8AEAD) else SunshineTextPrimary
                                                )
                                            }

                                            listOf("P-", "S-", "G-").forEach { pref ->
                                                Surface(
                                                    onClick = {
                                                        reuseError = null
                                                        reuseInput = pref
                                                    },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (reuseInput.startsWith(pref)) SunshineRedContainer else Color(0xFFF3EDED),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (reuseInput.startsWith(pref)) SunshineRed else SunshineBorder
                                                    )
                                                ) {
                                                    Text(
                                                        text = pref,
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 15.sp,
                                                        color = if (reuseInput.startsWith(pref)) SunshineRedDark else SunshineTextPrimary
                                                    )
                                                }
                                            }
                                        }

                                        // 4x3 Touch Keypad Grid
                                        val rows = listOf(
                                            listOf("1", "2", "3", "4"),
                                            listOf("5", "6", "7", "8"),
                                            listOf("9", "0", "LIMPAR", "APAGAR")
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
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
                                                            color = Color(0xFFF7F4F4),
                                                            border = BorderStroke(1.dp, Color(0xFFE5DFDE)),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = key,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = if (key.length > 1) 11.sp else 18.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = SunshineTextPrimary
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
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = SunshineGreen),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                        ) {
                                            Text(
                                                text = "Confirmar e Voltar à Fila com Prioridade",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // STEP 3: PRINTED THERMAL RECEIPT (2-COLUMN SPLIT: INSTRUCTIONS + 80MM RECEIPT)
                        TotemScreenStep.TICKET_PRINTED -> {
                            val ticket = printedTicket
                            if (ticket != null) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Column: Confirmation & Next Steps
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, SunshineBorder),
                                        modifier = Modifier
                                            .weight(0.55f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(28.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Surface(
                                                    color = SunshineGreenContainer,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = if (isReusedConfirmation) {
                                                            "SENHA REATIVADA COM PRIORIDADE PÓS-EXAME"
                                                        } else {
                                                            "TALÃO EMITIDO COM SUCESSO"
                                                        },
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = SunshineGreen
                                                    )
                                                }

                                                Text(
                                                    text = if (isReusedConfirmation) {
                                                        "A sua senha voltou à fila de atendimento"
                                                    } else {
                                                        "Retire o seu talão impresso abaixo"
                                                    },
                                                    fontSize = 26.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SunshineTextPrimary
                                                )

                                                Text(
                                                    text = "Aguarde confortavelmente na sala de espera. O seu número será chamado com anúncio de voz no ecrã da Smart TV.",
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp,
                                                    color = SunshineTextSecondary
                                                )
                                            }

                                            Button(
                                                onClick = { step = TotemScreenStep.HOME },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = SunshineRed),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(56.dp)
                                            ) {
                                                Text(
                                                    text = "Concluir e Novo Paciente",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }

                                    // Right Column: Realistic 80mm POS Thermal Paper Receipt
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.White,
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.5.dp, Color(0xFFDCD4D3)),
                                        modifier = Modifier
                                            .weight(0.45f)
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(
                                                    painter = painterResource(id = R.drawable.sunshine_logo),
                                                    contentDescription = "Sunshine Hospitals",
                                                    modifier = Modifier.height(30.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "RECEPÇÃO CENTRAL — TALÃO POS 80MM",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SunshineTextSecondary,
                                                    letterSpacing = 1.sp
                                                )
                                            }

                                            DashedReceiptDivider()

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = ticket.subServiceLabel,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SunshineTextSecondary,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = ticket.displayCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 54.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SunshineTextPrimary
                                                )
                                                if (ticket.isReturnFromExam) {
                                                    Surface(
                                                        color = Color(0xFFE8F5E9),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "PRIORIDADE: RETORNO DE EXAME",
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = SunshineGreen
                                                        )
                                                    }
                                                }
                                            }

                                            DashedReceiptDivider()

                                            Text(
                                                text = "Conserve este talão para reutilização após exames.",
                                                fontSize = 10.sp,
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

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Configurar Servidor API", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "No emulador Android, 10.0.2.2:5173 liga diretamente ao servidor local do seu PC.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        singleLine = true,
                        label = { Text("URL Base da API") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    PosApiService.baseUrl = serverUrlInput.trim()
                    showServerDialog = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun KioskPrimaryCard(
    modifier: Modifier = Modifier,
    badgeText: String,
    categoryTag: String,
    title: String,
    description: String,
    ctaText: String,
    accentColor: Color,
    containerTint: Color,
    iconType: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.5.dp, Color(0xFFE6DFDE)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(containerTint),
                        contentAlignment = Alignment.Center
                    ) {
                        KioskVectorIcon(type = iconType, tint = accentColor, size = 22.dp)
                    }

                    Surface(
                        color = containerTint.copy(alpha = 0.65f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = categoryTag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = title,
                        fontSize = 19.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SunshineTextPrimary
                    )
                }

                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    color = SunshineTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ctaText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    KioskVectorIcon(type = "ARROW_RIGHT", tint = Color.White, size = 16.dp)
                }
            }
        }
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

@Composable
private fun KioskVectorIcon(
    type: String,
    tint: Color,
    size: Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height

        when (type) {
            "MEDICAL" -> {
                // Clean Medical Cross inside rounded shield/circle
                drawCircle(color = tint, radius = w * 0.44f, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.28f),
                    end = Offset(w * 0.5f, h * 0.72f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.28f, h * 0.5f),
                    end = Offset(w * 0.72f, h * 0.5f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            "CALENDAR" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.22f),
                    size = Size(w * 0.72f, h * 0.64f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.14f, h * 0.42f),
                    end = Offset(w * 0.86f, h * 0.42f),
                    strokeWidth = 2.2.dp.toPx()
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.34f, h * 0.12f),
                    end = Offset(w * 0.34f, h * 0.28f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.66f, h * 0.12f),
                    end = Offset(w * 0.66f, h * 0.28f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            "SHIELD" -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.26f)
                    lineTo(w * 0.84f, h * 0.54f)
                    quadraticTo(w * 0.84f, h * 0.80f, w * 0.5f, h * 0.90f)
                    quadraticTo(w * 0.16f, h * 0.80f, w * 0.16f, h * 0.54f)
                    lineTo(w * 0.16f, h * 0.26f)
                    close()
                }
                drawPath(path = path, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.36f, h * 0.52f),
                    end = Offset(w * 0.47f, h * 0.63f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.47f, h * 0.63f),
                    end = Offset(w * 0.66f, h * 0.40f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            "RECEIPT" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.14f),
                    size = Size(w * 0.60f, h * 0.72f),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.34f, h * 0.36f),
                    end = Offset(w * 0.66f, h * 0.36f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.34f, h * 0.52f),
                    end = Offset(w * 0.66f, h * 0.52f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.34f, h * 0.68f),
                    end = Offset(w * 0.54f, h * 0.68f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            "RETURN" -> {
                drawArc(
                    color = tint,
                    startAngle = 35f,
                    sweepAngle = 275f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.62f, h * 0.14f),
                    end = Offset(w * 0.84f, h * 0.24f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.84f, h * 0.24f),
                    end = Offset(w * 0.68f, h * 0.42f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            "ARROW_RIGHT" -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.18f, h * 0.5f),
                    end = Offset(w * 0.82f, h * 0.5f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.56f, h * 0.24f),
                    end = Offset(w * 0.82f, h * 0.5f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.56f, h * 0.76f),
                    end = Offset(w * 0.82f, h * 0.5f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
