package com.pickle.assistant.llm

import com.pickle.assistant.config.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse

    @POST("chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): okhttp3.ResponseBody
}

interface AnthropicApi {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

class ApiClient(
    private val prefs: PreferencesRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var openAiApi: OpenAiApi? = null
    private var anthropicApi: AnthropicApi? = null
    private var currentBaseUrl: String = ""
    private var currentProtocol: String = ""
    private var currentApiKey: String = ""

    init {
        rebuildClients()
        observeConfigChanges()
    }

    private fun observeConfigChanges() {
        scope.launch {
            prefs.baseUrl.distinctUntilChanged().collect { url ->
                if (url != currentBaseUrl) {
                    currentBaseUrl = url
                    rebuildClients()
                }
            }
        }
        scope.launch {
            prefs.protocol.distinctUntilChanged().collect { protocol ->
                if (protocol != currentProtocol) {
                    currentProtocol = protocol
                    rebuildClients()
                }
            }
        }
        scope.launch {
            prefs.apiKey.distinctUntilChanged().collect { key ->
                currentApiKey = key ?: ""
            }
        }
    }

    private fun rebuildClients() {
        val baseUrl = prefs.getBaseUrl()
        val protocol = prefs.getProtocol()
        val apiKey = prefs.getApiKey() ?: ""

        currentBaseUrl = baseUrl
        currentProtocol = protocol
        currentApiKey = apiKey

        val client = OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor())
            .build()

        val json = Json { ignoreUnknownKeys = true }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        openAiApi = retrofit.create(OpenAiApi::class.java)
        anthropicApi = retrofit.create(AnthropicApi::class.java)
    }

    suspend fun sendChatRequest(
        messages: List<ChatMessage>,
        tools: List<Tool>? = PickleTools.allTools,
        temperature: Float = 0.2f,
        maxTokens: Int = 4096
    ): Result<ChatResponse> {
        return try {
            val protocol = prefs.getProtocol()
            val model = prefs.getModel()
            val apiKey = prefs.getApiKey() ?: return Result.failure(Exception("API key not configured"))

            when (protocol.lowercase()) {
                "openai" -> {
                    val request = ChatRequest(
                        model = model,
                        messages = messages,
                        temperature = temperature,
                        max_tokens = maxTokens,
                        tools = tools,
                        tool_choice = "auto"
                    )
                    val response = openAiApi!!.chatCompletion("Bearer $apiKey", request)
                    Result.success(response)
                }
                "anthropic" -> {
                    val anthropicMessages = messages.map { msg ->
                        AnthropicMessage(
                            role = when (msg.role) {
                                "system" -> "user"
                                "assistant" -> "assistant"
                                else -> "user"
                            },
                            content = msg.content ?: ""
                        )
                    }
                    
                    val systemPrompt = messages.firstOrNull { it.role == "system" }?.content
                    
                    val request = AnthropicRequest(
                        model = model,
                        messages = anthropicMessages,
                        max_tokens = maxTokens,
                        temperature = temperature,
                        system = systemPrompt,
                        tools = tools?.map { tool ->
                            AnthropicTool(
                                name = tool.function.name,
                                description = tool.function.description,
                                input_schema = tool.function.parameters
                            )
                        }
                    )
                    val response = anthropicApi!!.createMessage(apiKey, body = request)
                    
                    val chatResponse = convertAnthropicToOpenAi(response)
                    Result.success(chatResponse)
                }
                else -> Result.failure(Exception("Unknown protocol: $protocol"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertAnthropicToOpenAi(response: AnthropicResponse): ChatResponse {
        val content = response.content
            .filterIsInstance<AnthropicResponse.ContentBlock.Text>()
            .joinToString("") { it.text }
        
        val toolCalls = response.content
            .filterIsInstance<AnthropicResponse.ContentBlock.ToolUse>()
            .mapIndexed { index, toolUse ->
                ToolCall(
                    id = toolUse.id,
                    function = FunctionCall(
                        name = toolUse.name,
                        arguments = toolUse.input.toString()
                    )
                )
            }

        val message = ChatMessage(
            role = "assistant",
            content = content,
            tool_calls = if (toolCalls.isNotEmpty()) toolCalls else null
        )

        return ChatResponse(
            id = response.id,
            object = "chat.completion",
            created = System.currentTimeMillis() / 1000,
            model = response.model,
            choices = listOf(Choice(0, message, response.stop_reason)),
            usage = Usage(
                prompt_tokens = response.usage.input_tokens,
                completion_tokens = response.usage.output_tokens,
                total_tokens = response.usage.input_tokens + response.usage.output_tokens
            )
        )
    }

    suspend fun sendStreamingRequest(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: (Result<ChatResponse>) -> Unit,
        tools: List<Tool>? = PickleTools.allTools,
        temperature: Float = 0.2f,
        maxTokens: Int = 4096
    ) {
        scope.launch {
            try {
                val protocol = prefs.getProtocol()
                val model = prefs.getModel()
                val apiKey = prefs.getApiKey() ?: throw Exception("API key not configured")

                when (protocol.lowercase()) {
                    "openai" -> {
                        val request = ChatRequest(
                            model = model,
                            messages = messages,
                            temperature = temperature,
                            max_tokens = maxTokens,
                            stream = true,
                            tools = tools,
                            tool_choice = "auto"
                        )
                        
                        val response = openAiApi!!.chatCompletionStream("Bearer $apiKey", request)
                        val body = response.body()?.byteStream() ?: throw Exception("Empty response")
                        
                        body.bufferedReader().use { reader ->
                            var fullContent = ""
                            var toolCalls: MutableList<ToolCall>? = null
                            
                            reader.forEachLine { line ->
                                if (line.startsWith("data: ")) {
                                    val data = line.substring(6)
                                    if (data == "[DONE]") break
                                    
                                    try {
                                        val json = Json { ignoreUnknownKeys = true }
                                        val chunk = json.decodeFromString<StreamChunk>(data)
                                        chunk.choices.firstOrNull()?.let { choice ->
                                            choice.delta.content?.let { text ->
                                                fullContent += text
                                                onChunk(text)
                                            }
                                            choice.delta.tool_calls?.forEach { tc ->
                                                (toolCalls ?? mutableListOf()).apply {
                                                    if (index >= size) add(ToolCall(tc.id, "function", FunctionCall(tc.function?.name ?: "", "")))
                                                    else get(index).function.arguments += tc.function?.arguments ?: ""
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                            
                            val message = ChatMessage(
                                role = "assistant",
                                content = fullContent,
                                tool_calls = toolCalls?.takeIf { it.isNotEmpty() }
                            )
                            onComplete(Result.success(ChatResponse(
                                id = "stream_${System.currentTimeMillis()}",
                                object = "chat.completion",
                                created = System.currentTimeMillis() / 1000,
                                model = model,
                                choices = listOf(Choice(0, message, "stop")),
                                usage = null
                            )))
                        }
                    }
                    "anthropic" -> {
                        val result = sendChatRequest(messages, tools, temperature, maxTokens)
                        result.onSuccess { response ->
                            response.choices.firstOrNull()?.message?.content?.let { onChunk(it) }
                        }
                        onComplete(result)
                    }
                }
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    @Serializable
    private data class StreamChunk(
        val choices: List<StreamChoice>
    )

    @Serializable
    private data class StreamChoice(
        val index: Int,
        val delta: StreamDelta,
        val finish_reason: String?
    )

    @Serializable
    private data class StreamDelta(
        val role: String? = null,
        val content: String? = null,
        val tool_calls: List<StreamToolCall>? = null
    )

    @Serializable
    private data class StreamToolCall(
        val index: Int,
        val id: String,
        val type: String = "function",
        val function: StreamFunction? = null
    )

    @Serializable
    private data class StreamFunction(
        val name: String? = null,
        val arguments: String? = null
    )
}

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.currentTimeMillis()
        val response = chain.proceed(request)
        val took = System.currentTimeMillis() - start
        
        println("API ${request.method} ${request.url} -> ${response.code} (${took}ms)")
        
        return response
    }
}