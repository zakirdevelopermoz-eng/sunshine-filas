package com.example.sunshinesmarttv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    val connected: Boolean
)

object TvApiService {
    var baseUrl: String = "https://xpysyyfogouxapchvkwb.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_RJFeXPv9PuWg3YMgcajwaQ_bkDmfQ63"
    private const val LOCAL_FALLBACK_URL = "http://10.0.2.2:5173"

    private fun openSupabaseGet(path: String): HttpURLConnection {
        val url = URL("https://xpysyyfogouxapchvkwb.supabase.co/rest/v1/$path")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            connectTimeout = 3500
            readTimeout = 3500
        }
    }

    suspend fun fetchDashboardState(): TvDashboardState = withContext(Dispatchers.IO) {
        // 1. Try Supabase Cloud directly
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
                        currentTickets.add(
                            TvTicketItem(
                                id = obj.optString("id"),
                                displayCode = obj.optString("display_code"),
                                category = obj.optString("category", "PARTICULAR"),
                                subServiceLabel = obj.optString("sub_service_label", "Atendimento"),
                                status = obj.optString("status", "WAITING"),
                                isReturnFromExam = obj.optBoolean("is_return_from_exam", false),
                                assignedCounterId = obj.optString("assigned_counter_id", ""),
                                assignedCounterName = obj.optString("assigned_counter_name", "Balcão"),
                                callCount = obj.optInt("call_count", 0),
                                calledAt = obj.optString("called_at", ""),
                                queuedAt = obj.optString("queued_at", "")
                            )
                        )
                    }

                    return@withContext buildDashboardState(counters, currentTickets)
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback to Local Bridge (10.0.2.2:5173)
        try {
            val url = URL("$LOCAL_FALLBACK_URL/api/state")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
            }
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(raw)

                val cyclesArr = root.optJSONArray("cycles") ?: JSONArray()
                var currentCycleId = ""
                for (i in 0 until cyclesArr.length()) {
                    val c = cyclesArr.getJSONObject(i)
                    if (c.optBoolean("isCurrent", false)) {
                        currentCycleId = c.optString("id")
                    }
                }

                val countersArr = root.optJSONArray("counters") ?: JSONArray()
                val counters = mutableListOf<TvCounterItem>()
                for (i in 0 until countersArr.length()) {
                    val obj = countersArr.getJSONObject(i)
                    if (obj.optBoolean("isActive", true)) {
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
                }

                val ticketsArr = root.optJSONArray("tickets") ?: JSONArray()
                val currentTickets = mutableListOf<TvTicketItem>()
                for (i in 0 until ticketsArr.length()) {
                    val obj = ticketsArr.getJSONObject(i)
                    if (currentCycleId.isEmpty() || obj.optString("cycleId") == currentCycleId) {
                        currentTickets.add(
                            TvTicketItem(
                                id = obj.optString("id"),
                                displayCode = obj.optString("displayCode"),
                                category = obj.optString("category", "PARTICULAR"),
                                subServiceLabel = obj.optString("subServiceLabel", "Atendimento"),
                                status = obj.optString("status", "WAITING"),
                                isReturnFromExam = obj.optBoolean("isReturnFromExam", false),
                                assignedCounterId = obj.optString("assignedCounterId", ""),
                                assignedCounterName = obj.optString("assignedCounterName", "Balcão"),
                                callCount = obj.optInt("callCount", 0),
                                calledAt = obj.optString("calledAt", ""),
                                queuedAt = obj.optString("queuedAt", "")
                            )
                        )
                    }
                }

                return@withContext buildDashboardState(counters, currentTickets)
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
        val calledOrServing = currentTickets
            .filter { (it.status == "CALLED" || it.status == "IN_SERVICE") && it.calledAt.isNotEmpty() }
            .sortedByDescending { it.calledAt }

        val recentCalls = currentTickets
            .filter { it.calledAt.isNotEmpty() && it.status != "WAITING" }
            .sortedByDescending { it.calledAt }
            .take(4)

        val waiting = currentTickets
            .filter { it.status == "WAITING" }
            .sortedWith(
                compareByDescending<TvTicketItem> { it.isReturnFromExam }
                    .thenBy { it.queuedAt }
            )
            .take(6)

        return TvDashboardState(
            counters = counters,
            activeCall = calledOrServing.firstOrNull(),
            recentCalls = recentCalls,
            waitingTickets = waiting,
            connected = true
        )
    }
}
