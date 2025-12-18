/**
 * Code Review Agent - Main Entry Point
 *
 * This is a homework project demonstrating an AI agent built with Langchain4j
 * that can review code from GitHub Pull Requests.
 *
 * Features:
 * - Uses LLM (Ollama/OpenAI/LM Studio) for intelligent responses
 * - Tools for fetching PR details from GitHub API
 * - Static code analysis for common issues
 * - Interactive CLI for chatting with the agent
 *
 * Usage:
 *   ./gradlew run
 *
 * Environment variables:
 *   GITHUB_TOKEN - GitHub personal access token (optional, for higher API limits)
 *   OPENAI_API_KEY - OpenAI API key (required if using OpenAI provider)
 */

fun main() {
    println("""
        |=============================================
        |   Code Review Agent - AI Homework Project
        |=============================================
        |
        |This agent can help you review code from GitHub PRs.
        |
        |Available commands:
        |  - Type your question or request
        |  - Type 'quit' or 'exit' to stop
        |  - Type 'help' for usage examples
        |
    """.trimMargin())

    // Select LLM provider
    val provider = selectProvider()

    println("\nInitializing agent with $provider...")

    val agent = try {
        when (provider) {
            "ollama" -> {
                print("Enter Ollama base URL [http://localhost:11434]: ")
                val url = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "http://localhost:11434"
                print("Enter model name [llama3.2]: ")
                val model = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "llama3.2"
                AgentFactory.createOllamaCodeReviewer(url, model)
            }
            "openai" -> {
                print("Enter OpenAI model [gpt-4o-mini]: ")
                val model = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini"
                AgentFactory.createOpenAICodeReviewer(modelName = model)
            }
            "lmstudio" -> {
                print("Enter LM Studio base URL [http://localhost:1234/v1]: ")
                val url = readlnOrNull()?.takeIf { it.isNotBlank() } ?: "http://localhost:1234/v1"
                AgentFactory.createLMStudioCodeReviewer(url)
            }
            else -> {
                println("Using default Ollama configuration...")
                AgentFactory.createOllamaCodeReviewer()
            }
        }
    } catch (e: Exception) {
        println("Error initializing agent: ${e.message}")
        println("Please make sure your LLM server is running.")
        return
    }

    println("\nAgent ready! Start chatting.\n")

    // Interactive chat loop
    while (true) {
        print("You: ")
        val input = readlnOrNull()?.trim() ?: continue

        when (input.lowercase()) {
            "quit", "exit", "q" -> {
                println("\nGoodbye!")
                break
            }
            "help" -> {
                printHelp()
                continue
            }
            "" -> continue
        }

        try {
            println("\nAgent: Thinking...")
            val response = agent.chat(input)
            println("\nAgent: $response\n")
        } catch (e: Exception) {
            println("\nError: ${e.message}\n")
        }
    }
}

/**
 * Interactive provider selection
 */
fun selectProvider(): String {
    println("Select LLM provider:")
    println("  1. Ollama (local)")
    println("  2. OpenAI (requires API key)")
    println("  3. LM Studio (local, OpenAI-compatible)")
    print("\nChoice [1]: ")

    val choice = readlnOrNull()?.trim() ?: "1"

    return when (choice) {
        "2" -> "openai"
        "3" -> "lmstudio"
        else -> "ollama"
    }
}

/**
 * Print help message with usage examples
 */
fun printHelp() {
    println("""
        |
        |=============================================
        |   Code Review Agent - Help
        |=============================================
        |
        |Example queries:
        |
        |1. Review a GitHub PR:
        |   "Review PR #123 in owner/repo"
        |   "List open PRs in facebook/react"
        |   "Get the diff for PR #456 in microsoft/vscode"
        |
        |2. Analyze code:
        |   "Analyze this Kotlin code for issues: fun test() { println(password!!) }"
        |   "Check this Python code: eval(user_input)"
        |
        |3. Generate review summary:
        |   "Generate a review summary for 'Add login feature' with findings: good tests, needs docs"
        |
        |4. Read local files:
        |   "Read and review the file /path/to/MyClass.kt"
        |
        |Note: For GitHub API access, set GITHUB_TOKEN environment variable
        |      to increase rate limits and access private repos.
        |
        |Commands:
        |  help  - Show this help message
        |  quit  - Exit the program
        |
    """.trimMargin())
}
