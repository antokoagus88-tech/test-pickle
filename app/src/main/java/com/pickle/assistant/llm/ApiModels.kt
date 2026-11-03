package com.pickle.assistant.llm

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.2f,
    val max_tokens: Int = 4096,
    val stream: Boolean = false,
    val tools: List<Tool>? = null,
    val tool_choice: Any? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String?,
    val name: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDef
)

@Serializable
data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

@Serializable
data class ChatResponse(
    val id: String,
    val object: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String?
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val max_tokens: Int = 4096,
    val temperature: Float = 0.2f,
    val system: String? = null,
    val tools: List<AnthropicTool>? = null,
    val tool_choice: Any? = null
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: Any
)

@Serializable
sealed class ContentBlock {
    @Serializable
    data class Text(val type: String = "text", val text: String) : ContentBlock()
    @Serializable
    data class ToolUse(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: Map<String, Any>
    ) : ContentBlock()
    @Serializable
    data class ToolResult(
        val type: String = "tool_result",
        val tool_use_id: String,
        val content: Any
    ) : ContentBlock()
}

@Serializable
data class AnthropicTool(
    val name: String,
    val description: String,
    val input_schema: Map<String, Any>
)

@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)

@Serializable
data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

@Serializable
data class ApiError(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String?,
    val code: String?
)

object PickleTools {
    val SEND_REPLY = Tool(
        function = FunctionDef(
            name = "send_reply",
            description = "Kirim balasan ke notifikasi pesan (WhatsApp, Telegram, SMS, dll) via RemoteInput",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "package_name" to mapOf("type" to "string", "description" to "Package name aplikasi pengirim"),
                    "notification_id" to mapOf("type" to "integer", "description" to "ID notifikasi"),
                    "notification_tag" to mapOf("type" to "string", "description" to "Tag notifikasi"),
                    "reply_text" to mapOf("type" to "string", "description" to "Teks balasan yang akan dikirim")
                ),
                "required" to listOf("package_name", "notification_id", "reply_text")
            )
        )
    )

    val GET_NOTIFICATIONS = Tool(
        function = FunctionDef(
            name = "get_recent_notifications",
            description = "Ambil daftar notifikasi terbaru dari aplikasi target",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "limit" to mapOf("type" to "integer", "description" to "Jumlah notifikasi maksimal", "default" to 10),
                    "package_name" to mapOf("type" to "string", "description" to "Filter by package name (optional)")
                ),
                "required" to listOf()
            )
        )
    )

    val READ_NOTIFICATION_CONTENT = Tool(
        function = FunctionDef(
            name = "read_notification_content",
            description = "Baca isi lengkap notifikasi termasuk pesan-pesan di dalamnya",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "package_name" to mapOf("type" to "string", "description" to "Package name aplikasi"),
                    "notification_id" to mapOf("type" to "integer", "description" to "ID notifikasi"),
                    "notification_tag" to mapOf("type" to "string", "description" to "Tag notifikasi")
                ),
                "required" to listOf("package_name", "notification_id")
            )
        )
    )

    val allTools = listOf(SEND_REPLY, GET_NOTIFICATIONS, READ_NOTIFICATION_CONTENT)
}