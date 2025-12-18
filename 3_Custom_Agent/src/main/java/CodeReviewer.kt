import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.P
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import java.util.concurrent.TimeUnit

/**
 * CodeReviewer - An AI Agent that reviews code from GitHub Pull Requests
 *
 * This class provides tools for the AI agent to:
 * - Fetch PR details from GitHub API
 * - Analyze code changes (diffs)
 * - Check for common code issues and security vulnerabilities
 * - Generate structured review summaries
 */
class CodeReviewer(
    private val githubToken: String? = System.getenv("GITHUB_TOKEN")
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Tool: Fetches Pull Request details from GitHub
     */
    @Tool("Fetches details of a GitHub Pull Request including title, description, author, and state")
    fun getPullRequestDetails(
        @P("Owner of the repository (e.g., 'octocat')") owner: String,
        @P("Name of the repository (e.g., 'hello-world')") repo: String,
        @P("Pull request number") prNumber: Int
    ): String {
        val url = "https://api.github.com/repos/$owner/$repo/pulls/$prNumber"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "CodeReviewer-Agent")

        githubToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return "Error: Empty response"

            if (!response.isSuccessful) {
                return "Error fetching PR: ${response.code} - $body"
            }

            val json = gson.fromJson(body, JsonObject::class.java)

            """
            |Pull Request #$prNumber
            |================================
            |Title: ${json.get("title")?.asString ?: "N/A"}
            |Author: ${json.getAsJsonObject("user")?.get("login")?.asString ?: "N/A"}
            |State: ${json.get("state")?.asString ?: "N/A"}
            |Base Branch: ${json.getAsJsonObject("base")?.get("ref")?.asString ?: "N/A"}
            |Head Branch: ${json.getAsJsonObject("head")?.get("ref")?.asString ?: "N/A"}
            |Created: ${json.get("created_at")?.asString ?: "N/A"}
            |Changed Files: ${json.get("changed_files")?.asInt ?: 0}
            |Additions: +${json.get("additions")?.asInt ?: 0}
            |Deletions: -${json.get("deletions")?.asInt ?: 0}
            |
            |Description:
            |${json.get("body")?.asString ?: "No description provided"}
            """.trimMargin()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Tool: Fetches the diff/changes of a Pull Request
     */
    @Tool("Fetches the code diff (changes) of a GitHub Pull Request")
    fun getPullRequestDiff(
        @P("Owner of the repository") owner: String,
        @P("Name of the repository") repo: String,
        @P("Pull request number") prNumber: Int
    ): String {
        val url = "https://api.github.com/repos/$owner/$repo/pulls/$prNumber/files"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "CodeReviewer-Agent")

        githubToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return "Error: Empty response"

            if (!response.isSuccessful) {
                return "Error fetching diff: ${response.code} - $body"
            }

            val files = gson.fromJson(body, JsonArray::class.java)
            val result = StringBuilder()
            result.appendLine("Changed Files in PR #$prNumber")
            result.appendLine("================================")

            files.forEach { file ->
                val fileObj = file.asJsonObject
                val filename = fileObj.get("filename")?.asString ?: "unknown"
                val status = fileObj.get("status")?.asString ?: "modified"
                val additions = fileObj.get("additions")?.asInt ?: 0
                val deletions = fileObj.get("deletions")?.asInt ?: 0
                val patch = fileObj.get("patch")?.asString ?: ""

                result.appendLine("\nFile: $filename")
                result.appendLine("Status: $status | +$additions -$deletions")
                if (patch.isNotEmpty()) {
                    result.appendLine("Diff:")
                    patch.lines().take(50).forEach { line ->
                        result.appendLine("  $line")
                    }
                    if (patch.lines().size > 50) {
                        result.appendLine("  ... (truncated)")
                    }
                }
            }

            result.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Tool: Analyzes code for common issues
     */
    @Tool("Analyzes code snippet for common issues like security vulnerabilities, code smells, and best practices")
    fun analyzeCode(
        @P("The code snippet to analyze") code: String,
        @P("Programming language of the code (e.g., 'kotlin', 'java', 'python')") language: String
    ): String {
        val issues = mutableListOf<String>()

        // Security checks
        if (code.contains("password", ignoreCase = true) &&
            (code.contains("=") || code.contains("\""))) {
            issues.add("SECURITY: Possible hardcoded password detected")
        }

        if (code.contains("api_key", ignoreCase = true) ||
            code.contains("apikey", ignoreCase = true) ||
            code.contains("secret", ignoreCase = true)) {
            issues.add("SECURITY: Possible hardcoded API key or secret")
        }

        if (code.contains("TODO", ignoreCase = true) || code.contains("FIXME", ignoreCase = true)) {
            issues.add("NOTE: TODO/FIXME comment found - ensure it's addressed")
        }

        // Code quality checks
        if (code.lines().any { it.length > 120 }) {
            issues.add("STYLE: Some lines exceed 120 characters - consider breaking them up")
        }

        if (code.contains("catch") && code.contains("Exception") &&
            !code.contains("log", ignoreCase = true)) {
            issues.add("WARNING: Exception caught but possibly not logged")
        }

        // Language-specific checks
        when (language.lowercase()) {
            "kotlin", "java" -> {
                if (code.contains("!!")) {
                    issues.add("KOTLIN: Use of !! (not-null assertion) - consider safer alternatives")
                }
                if (code.contains("var ") && !code.contains("val ")) {
                    issues.add("KOTLIN: Consider using 'val' instead of 'var' where possible")
                }
                if (code.contains("println") || code.contains("System.out")) {
                    issues.add("JAVA/KOTLIN: Use proper logging instead of println/System.out")
                }
            }
            "python" -> {
                if (code.contains("eval(") || code.contains("exec(")) {
                    issues.add("SECURITY: Use of eval/exec detected - potential code injection risk")
                }
                if (code.contains("import *")) {
                    issues.add("PYTHON: Avoid wildcard imports")
                }
            }
            "javascript", "typescript" -> {
                if (code.contains("eval(")) {
                    issues.add("SECURITY: Use of eval() detected - potential security risk")
                }
                if (code.contains("var ")) {
                    issues.add("JS: Consider using 'const' or 'let' instead of 'var'")
                }
                if (code.contains("innerHTML")) {
                    issues.add("SECURITY: Use of innerHTML - potential XSS vulnerability")
                }
            }
        }

        return if (issues.isEmpty()) {
            "No obvious issues found in the code snippet"
        } else {
            """
            |Code Analysis Results
            |================================
            |Found ${issues.size} potential issue(s):
            |
            |${issues.mapIndexed { i, issue -> "${i + 1}. $issue" }.joinToString("\n")}
            """.trimMargin()
        }
    }

    /**
     * Tool: Generates a review summary
     */
    @Tool("Generates a structured code review summary based on the provided information")
    fun generateReviewSummary(
        @P("Title of the PR") prTitle: String,
        @P("Key findings from the review (comma-separated)") findings: String,
        @P("Overall assessment: APPROVE, REQUEST_CHANGES, or COMMENT") assessment: String
    ): String {
        val status = when (assessment.uppercase()) {
            "APPROVE" -> "APPROVED"
            "REQUEST_CHANGES" -> "CHANGES REQUESTED"
            else -> "COMMENTED"
        }

        val findingsList = findings.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return """
        |Code Review Summary
        |================================
        |PR: $prTitle
        |Status: $status
        |
        |Findings:
        |${findingsList.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")}
        |
        |================================
        |Generated by CodeReviewer Agent
        """.trimMargin()
    }

    /**
     * Tool: Lists open PRs in a repository
     */
    @Tool("Lists open Pull Requests in a GitHub repository")
    fun listOpenPullRequests(
        @P("Owner of the repository") owner: String,
        @P("Name of the repository") repo: String
    ): String {
        val url = "https://api.github.com/repos/$owner/$repo/pulls?state=open"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "CodeReviewer-Agent")

        githubToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return "Error: Empty response"

            if (!response.isSuccessful) {
                return "Error fetching PRs: ${response.code} - $body"
            }

            val prs = gson.fromJson(body, JsonArray::class.java)

            if (prs.size() == 0) {
                return "No open Pull Requests found in $owner/$repo"
            }

            val result = StringBuilder()
            result.appendLine("Open Pull Requests in $owner/$repo")
            result.appendLine("================================")

            prs.forEach { pr ->
                val prObj = pr.asJsonObject
                val number = prObj.get("number")?.asInt ?: 0
                val title = prObj.get("title")?.asString ?: "N/A"
                val author = prObj.getAsJsonObject("user")?.get("login")?.asString ?: "N/A"

                result.appendLine("#$number - $title (by $author)")
            }

            result.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Tool: Reads a local file for review
     */
    @Tool("Reads a local file from the filesystem for code review")
    fun readLocalFile(
        @P("Path to the file to read") filePath: String
    ): String {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                return "Error: File not found: $filePath"
            }
            if (file.length() > 100_000) {
                return "Error: File too large (>100KB). Please specify a smaller file."
            }

            """
            |File: $filePath
            |================================
            |${file.readText()}
            """.trimMargin()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }
}
