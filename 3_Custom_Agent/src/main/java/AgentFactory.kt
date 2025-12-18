import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage

/**
 * AgentFactory - Factory for creating AI agent instances with configured LLM models.
 *
 * Supports multiple LLM providers:
 * - Ollama (local models)
 * - OpenAI (GPT models)
 * - LM Studio (local, OpenAI-compatible)
 */
object AgentFactory {

    // Default configuration for local LLM (LM Studio / Ollama)
    private const val DEFAULT_LOCAL_URL = "http://localhost:1234/v1"
    private const val DEFAULT_LOCAL_MODEL = "local-model"

    // OpenAI configuration
    private const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"

    /**
     * LLM Provider types supported by the factory
     */
    enum class LLMProvider {
        OLLAMA,      // Local Ollama instance
        OPENAI,      // OpenAI API
        LM_STUDIO    // LM Studio (OpenAI-compatible local)
    }

    /**
     * Creates a chat model based on the specified provider.
     *
     * @param provider The LLM provider to use
     * @param baseUrl Base URL for local providers (Ollama, LM Studio)
     * @param modelName Model name/identifier
     * @param apiKey API key for cloud providers (OpenAI)
     * @param temperature Creativity/randomness parameter (0.0 - 1.0)
     */
    fun createChatModel(
        provider: LLMProvider = LLMProvider.OLLAMA,
        baseUrl: String = DEFAULT_LOCAL_URL,
        modelName: String? = null,
        apiKey: String? = null,
        temperature: Double = 0.7
    ): ChatLanguageModel {
        return when (provider) {
            LLMProvider.OLLAMA -> {
                OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName ?: "llama3.2")
                    .temperature(temperature)
                    .build()
            }

            LLMProvider.OPENAI -> {
                val key = apiKey ?: System.getenv("OPENAI_API_KEY")
                    ?: throw IllegalStateException("OpenAI API key not provided. Set OPENAI_API_KEY environment variable.")

                OpenAiChatModel.builder()
                    .apiKey(key)
                    .modelName(modelName ?: DEFAULT_OPENAI_MODEL)
                    .temperature(temperature)
                    .build()
            }

            LLMProvider.LM_STUDIO -> {
                // LM Studio provides OpenAI-compatible API
                OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey("lm-studio") // LM Studio doesn't require real API key
                    .modelName(modelName ?: DEFAULT_LOCAL_MODEL)
                    .temperature(temperature)
                    .build()
            }
        }
    }

    /**
     * Creates a CodeReviewerAgent - an AI agent that can review code using tools.
     *
     * The agent has access to these tools:
     * - getPullRequestDetails: Fetch PR info from GitHub
     * - getPullRequestDiff: Get code changes from a PR
     * - analyzeCode: Static analysis for common issues
     * - generateReviewSummary: Create structured review output
     * - listOpenPullRequests: List open PRs in a repository
     * - readLocalFile: Read local files for review
     *
     * @param model The chat model to use for the agent
     * @param githubToken Optional GitHub token for API access
     */
    fun createCodeReviewerAgent(
        model: ChatLanguageModel,
        githubToken: String? = System.getenv("GITHUB_TOKEN")
    ): CodeReviewerAgent {
        val tools = CodeReviewer(githubToken)

        return AiServices.builder(CodeReviewerAgent::class.java)
            .chatLanguageModel(model)
            .tools(tools)
            .build()
    }

    /**
     * Quick factory method to create a CodeReviewer agent with Ollama.
     */
    fun createOllamaCodeReviewer(
        baseUrl: String = "http://localhost:11434",
        modelName: String = "llama3.2"
    ): CodeReviewerAgent {
        val model = createChatModel(
            provider = LLMProvider.OLLAMA,
            baseUrl = baseUrl,
            modelName = modelName,
            temperature = 0.3
        )
        return createCodeReviewerAgent(model)
    }

    /**
     * Quick factory method to create a CodeReviewer agent with OpenAI.
     */
    fun createOpenAICodeReviewer(
        apiKey: String? = null,
        modelName: String = "gpt-4o-mini"
    ): CodeReviewerAgent {
        val model = createChatModel(
            provider = LLMProvider.OPENAI,
            apiKey = apiKey,
            modelName = modelName,
            temperature = 0.3
        )
        return createCodeReviewerAgent(model)
    }

    /**
     * Quick factory method to create a CodeReviewer agent with LM Studio.
     */
    fun createLMStudioCodeReviewer(
        baseUrl: String = "http://localhost:1234/v1",
        modelName: String = "local-model"
    ): CodeReviewerAgent {
        val model = createChatModel(
            provider = LLMProvider.LM_STUDIO,
            baseUrl = baseUrl,
            modelName = modelName,
            temperature = 0.3
        )
        return createCodeReviewerAgent(model)
    }
}

/**
 * CodeReviewerAgent interface - defines the AI agent's conversational interface.
 *
 * This interface is implemented by Langchain4j's AiServices, which automatically
 * connects the LLM with the tools provided during creation.
 */
interface CodeReviewerAgent {
    /**
     * Chat with the code reviewer agent.
     * The agent can use tools to read local files, analyze code, and optionally fetch PR details.
     *
     * @param message User's message/request
     * @return Agent's response
     */
    @SystemMessage("""
        You are an expert Code Review Agent specialized in reviewing local code files.

        PRIMARY FUNCTION: Review local code files for quality, bugs, security issues, and best practices.

        Available tools:
        - readLocalFile(filePath): Read any local file by its path - USE THIS FIRST to get file contents
        - analyzeCode(code, language): Perform static analysis on code snippets to detect issues
        - generateReviewSummary(findings, severity, recommendations): Create structured review reports
        - getPullRequestDetails(owner, repo, prNumber): Fetch PR info from GitHub (optional)
        - getPullRequestDiff(owner, repo, prNumber): Get code changes from a PR (optional)
        - listOpenPullRequests(owner, repo): List open PRs in a repository (optional)

        WORKFLOW for local file review:
        1. When user provides a file path, use readLocalFile to get the contents
        2. Use analyzeCode to perform static analysis on the code
        3. Provide detailed feedback on:
           - Code quality and readability
           - Potential bugs or errors
           - Security vulnerabilities
           - Performance issues
           - Best practices and coding standards
           - Suggestions for improvement
        4. Use generateReviewSummary to create a structured final report

        RESPONSE FORMAT:
        - Be specific and reference line numbers when possible
        - Categorize issues by severity (Critical, Warning, Info)
        - Provide actionable recommendations
        - Include code examples for suggested fixes when helpful

        If the user provides just a filename without a path, ask for the full file path.
        If you encounter errors reading files, explain the issue and ask for clarification.
    """)
    fun chat(message: String): String
}
