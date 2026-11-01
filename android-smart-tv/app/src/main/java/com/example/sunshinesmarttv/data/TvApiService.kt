package com.example.sunshinesmarttv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class TvCounterItem(
    val id: String,
    val code: String,
    val name: String,
    val subtitle: String,
    val isActive: Boolean
)

data class TvTicketItem(
    val id: String,
    val displayCode: String,
    val category: String,
    val subServiceLabel: String,
    val status: String,
    val isReturnFromExam: Boolean,
    val assignedCounterId: String,
    val assignedCounterName: String,
    val callCount: Int,
    val calledAt: String,
    val queuedAt: String
)

data class TvDashboardState(
    val counters: List<TvCounterItem>,
    val activeCall: TvTicketItem?,
    val recentCalls: List<TvTicketItem>,
    val waitingTickets: List<TvTicketItem>,
    val latestCalledAt: String = "",
    val connected: Boolean
)

object TvApiService {
    var baseUrl: String = "https://xpysyyfogouxapchvkwb.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_RJFeXPv9PuWg3YMgcajwaQ_bkDmfQ63"

    // Resilient SSL socket factory so Smart TVs / Emulators with out-of-sync clocks never fail TLS handshake
    private val sslSocketFactory by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    private fun openSupabaseGet(path: String): HttpURLConnection {
        val url = URL("https://xpysyyfogouxapchvkwb.supabase.co/rest/v1/$path")
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = this@TvApiService.sslSocketFactory
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            setRequestProperty("Cache-Control", "no-cache")
            connectTimeout = 4500
            readTimeout = 4500
        }
    }

    fun downloadAudioBytes(urlString: String): ByteArray? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = this@TvApiService.sslSocketFactory
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchDashboardState(): TvDashboardState = withContext(Dispatchers.IO) {
        try {
            val cycleConn = openSupabaseGet("queue_cycles?is_current=eq.true&select=id&limit=1")
            if (cycleConn.responseCode in 200..299) {
                val cyclesArr = JSONArray(cycleConn.inputStream.bufferedReader().use { it.readText() })
                val currentCycleId = if (cyclesArr.length() > 0) cyclesArr.getJSONObject(0).optString("id") else ""

                val countersConn = openSupabaseGet("counters?is_active=eq.true&order=display_order.asc")
                val ticketsConn = if (currentCycleId.isNotEmpty()) {
                    openSupabaseGet("tickets?cycle_id=eq.$currentCycleId&order=created_at.asc")
                } else {
                    openSupabaseGet("tickets?order=created_at.asc")
                }

                if (countersConn.responseCode in 200..299 && ticketsConn.responseCode in 200..299) {
                    val countersArr = JSONArray(countersConn.inputStream.bufferedReader().use { it.readText() })
                    val ticketsArr = JSONArray(ticketsConn.inputStream.bufferedReader().use { it.readText() })

                    val counters = mutableListOf<TvCounterItem>()
                    for (i in 0 until countersArr.length()) {
                        val obj = countersArr.getJSONObject(i)
                        counters.add(
                            TvCounterItem(
                                id = obj.optString("id"),
                                code = obj.optString("code"),
                                name = obj.optString("name"),
                                subtitle = obj.optString("subtitle"),
                                isActive = true
                            )
                        )
                    }

                    val currentTickets = mutableListOf<TvTicketItem>()
                    for (i in 0 until ticketsArr.length()) {
                        val obj = ticketsArr.getJSONObject(i)
                        val rawCalledAt = if (obj.isNull("called_at")) "" else obj.optString("called_at", "")
                        val rawCounterId = if (obj.isNull("assigned_counter_id")) "" else obj.optString("assigned_counter_id", "")
                        val rawCounterName = if (obj.isNull("assigned_counter_name")) "Balcão" else obj.optString("assigned_counter_name", "Balcão")
                        currentTickets.add(
                            TvTicketItem(
                                id = obj.optString("id"),
                                displayCode = obj.optString("display_code"),
                                category = obj.optString("category", "PARTICULAR"),
                                subServiceLabel = obj.optString("sub_service_label", "Atendimento"),
                                status = obj.optString("status", "WAITING"),
                                isReturnFromExam = obj.optBoolean("is_return_from_exam", false),
                                assignedCounterId = rawCounterId,
                                assignedCounterName = rawCounterName,
                                callCount = obj.optInt("call_count", 0),
                                calledAt = if (rawCalledAt == "null") "" else rawCalledAt,
                                queuedAt = if (obj.isNull("queued_at")) "" else obj.optString("queued_at", "")
                            )
                        )
                    }

                    return@withContext buildDashboardState(counters, currentTickets)
                }
            }
        } catch (_: Exception) {
        }

        TvDashboardState(
            counters = emptyList(),
            activeCall = null,
            recentCalls = emptyList(),
            waitingTickets = emptyList(),
            connected = false
        )
    }

    private fun buildDashboardState(
        counters: List<TvCounterItem>,
        currentTickets: List<TvTicketItem>
    ): TvDashboardState {
        // Find the most recently called ticket across all counters (ignoring WAITING tickets with empty calledAt)
        val allCalledSorted = currentTickets
            .filter { it.status != "WAITING" && it.calledAt.isNotEmpty() && it.calledAt != "null" }
            .sortedByDescending { it.calledAt }

        val mostRecentCalledTicket = allCalledSorted.firstOrNull()
        val latestCalledAt = mostRecentCalledTicket?.calledAt ?: ""

        // Only display activeCall if the most recently called ticket is STILL actively CALLED or IN_SERVICE.
        // Never fall back to an older stale ticket from another counter after a patient finishes service.
        val activeCall = if (
            mostRecentCalledTicket != null &&
            (mostRecentCalledTicket.status == "CALLED" || mostRecentCalledTicket.status == "IN_SERVICE")
        ) {
            mostRecentCalledTicket
        } else {
            null
        }

        // For the bottom counter status strip (Balcão A, B, C, D), only show tickets currently CALLED or IN_SERVICE
        val currentlyServingByCounter = currentTickets
            .filter { (it.status == "CALLED" || it.status == "IN_SERVICE") && it.calledAt.isNotEmpty() }
            .sortedByDescending { it.calledAt }

        val waiting = currentTickets
            .filter { it.status == "WAITING" }
            .sortedWith(
                compareByDescending<TvTicketItem> { it.isReturnFromExam }
                    .thenBy { it.queuedAt }
            )

        return TvDashboardState(
            counters = counters,
            activeCall = activeCall,
            recentCalls = currentlyServingByCounter,
            waitingTickets = waiting,
            latestCalledAt = latestCalledAt,
            connected = true
        )
    }
}
