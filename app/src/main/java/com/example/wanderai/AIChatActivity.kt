package com.wanderai.travelplanner

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GuideChatMessage(
    var text: String,
    val isUser: Boolean
)

class AIChatActivity : BaseNavigationActivity() {

    private lateinit var tvChatTripContext: TextView
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendMessage: Button

    private val messages = mutableListOf<GuideChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private var origin: String = ""
    private var destination: String = ""
    private var days: Int = 3
    private var members: Int = 2

    override fun getSelectedNavigationItemId(): Int = R.id.nav_route_builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        tvChatTripContext = findViewById(R.id.tvChatTripContext)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        etChatMessage = findViewById(R.id.etChatMessage)
        btnSendMessage = findViewById(R.id.btnSendMessage)

        val btnBack = findViewById<MaterialButton>(R.id.btnBack)
        val chipMustVisit = findViewById<TextView>(R.id.chipMustVisit)
        val chipLocalFood = findViewById<TextView>(R.id.chipLocalFood)
        val chipBudgetTips = findViewById<TextView>(R.id.chipBudgetTips)

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        origin = intent.getStringExtra(IntentNavigator.EXTRA_ORIGIN)
            ?: prefs.getString("LAST_ORIGIN", "").orEmpty()
        destination = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?: prefs.getString("LAST_DESTINATION", "").orEmpty()
        days = prefs.getInt("LAST_DAYS", 3).coerceAtLeast(1)
        members = prefs.getInt("LAST_MEMBERS", 2).coerceAtLeast(1)

        if (destination.isNotEmpty()) {
            tvChatTripContext.text = "Trip: ${origin.ifEmpty { "Origin" }} → $destination ($days days)"
        } else {
            tvChatTripContext.text = "AI Assistant • Ready to guide"
        }

        chatAdapter = ChatAdapter(messages)
        rvChatMessages.apply {
            layoutManager = LinearLayoutManager(this@AIChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        val welcomeText = if (destination.isNotEmpty()) {
            "Hello! I'm your WanderAI trip guide. Ask me anything about your $days-day trip to $destination!"
        } else {
            "Hello! I'm your WanderAI trip guide. Where are you planning to travel, or what kind of trip are you looking for?"
        }
        addMessage(GuideChatMessage(welcomeText, isUser = false))

        btnBack.setOnClickListener { finish() }

        btnSendMessage.setOnClickListener {
            val text = etChatMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                etChatMessage.setText("")
                sendUserPrompt(text)
            }
        }

        etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = etChatMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    etChatMessage.setText("")
                    sendUserPrompt(text)
                }
                true
            } else {
                false
            }
        }

        chipMustVisit.setOnClickListener {
            sendUserPrompt("What are the top must-visit spots for this trip?")
        }
        chipLocalFood.setOnClickListener {
            sendUserPrompt("What local foods and restaurants should we try?")
        }
        chipBudgetTips.setOnClickListener {
            sendUserPrompt("Give me 4 practical money-saving tips for this trip.")
        }
    }

    private fun addMessage(msg: GuideChatMessage): Int {
        messages.add(msg)
        val index = messages.lastIndex
        chatAdapter.notifyItemInserted(index)
        rvChatMessages.scrollToPosition(index)
        return index
    }

    private fun updateMessage(index: Int, newText: String) {
        if (index in messages.indices) {
            messages[index].text = newText
            chatAdapter.notifyItemChanged(index)
            rvChatMessages.scrollToPosition(index)
        }
    }

    private fun sendUserPrompt(userPrompt: String) {
        addMessage(GuideChatMessage(userPrompt, isUser = true))
        val aiBubbleIndex = addMessage(GuideChatMessage("Thinking...", isUser = false))

        btnSendMessage.isEnabled = false

        lifecycleScope.launch {
            try {
                val tripInfo = if (destination.isNotEmpty()) {
                    "Trip context: From ${origin.ifEmpty { "starting city" }} to $destination for $days days with $members travelers. "
                } else {
                    "General travel planning query. "
                }

                val fullPrompt = """
                    You are WanderAI, a concise, friendly, and practical travel guide.
                    $tripInfo
                    Answer clearly with short bullet points:
                    $userPrompt
                """.trimIndent()

                val reply = callGeminiRestApi(fullPrompt)
                updateMessage(aiBubbleIndex, reply)
            } catch (e: Exception) {
                Log.e("WANDER_AI_CHAT", "REST error", e)
                updateMessage(
                    aiBubbleIndex,
                    "Unable to reach WanderAI: ${e.message ?: "Network error"}"
                )
            } finally {
                btnSendMessage.isEnabled = true
            }
        }
    }

    private suspend fun callGeminiRestApi(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.ifEmpty { BuildConfig.MAPS_API_KEY }
        if (apiKey.isEmpty()) {
            throw Exception("Missing GEMINI_API_KEY in local.properties")
        }

        // Active 2026 Gemini models ordered from primary Flash to high-throughput Lite fallbacks
        val models = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
        )

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", "LOW")
                })
            })
        }.toString().toByteArray(Charsets.UTF_8)

        var lastError = "Service temporarily unavailable"

        for (model in models) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                }

                conn.outputStream.use { os ->
                    os.write(requestBody)
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code in 200..299) {
                    val json = JSONObject(responseText)
                    val reply = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    if (reply.isNotEmpty()) return@withContext reply
                } else {
                    lastError = try {
                        JSONObject(responseText).getJSONObject("error").getString("message")
                    } catch (_: Exception) {
                        "HTTP $code"
                    }
                    Log.w("WANDER_AI_CHAT", "Model $model failed ($code): $lastError. Trying fallback...")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
                Log.w("WANDER_AI_CHAT", "Model $model exception: $lastError")
            }
        }

        throw Exception(lastError)
    }

    inner class ChatAdapter(
        private val items: List<GuideChatMessage>
    ) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

        inner class ViewHolder(
            val wrapper: LinearLayout,
            val bubble: TextView
        ) : RecyclerView.ViewHolder(wrapper)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * d).toInt()

            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            }

            val bubble = TextView(ctx).apply {
                textSize = 14f
                setPadding(dp(16), dp(12), dp(16), dp(12))
                maxWidth = (ctx.resources.displayMetrics.widthPixels * 0.78).toInt()
            }

            wrapper.addView(bubble)
            return ViewHolder(wrapper, bubble)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val ctx = holder.wrapper.context

            if (item.isUser) {
                holder.wrapper.gravity = Gravity.END
                holder.bubble.background = ContextCompat.getDrawable(ctx, R.drawable.bg_pill_button)
                holder.bubble.setTextColor(Color.WHITE)
                holder.bubble.setTypeface(null, Typeface.NORMAL)
            } else {
                holder.wrapper.gravity = Gravity.START
                holder.bubble.background = ContextCompat.getDrawable(ctx, R.drawable.bg_card_glass)
                holder.bubble.setTextColor(ContextCompat.getColor(ctx, R.color.text_high_emphasis))
                holder.bubble.setTypeface(null, Typeface.NORMAL)
            }
            holder.bubble.text = item.text
        }

        override fun getItemCount(): Int = items.size
    }
}