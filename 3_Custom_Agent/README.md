# Praktické cvičení - Lekce 5
# Custom AI Agent

---

## Purpose
AI-powered code reviewer that analyzes GitHub PRs and local files through natural language interface.

## Diagram
```
User → CLI → AI Agent → 6 Tools → GitHub API / Local Files
```

## Implementation
- **Tools (6):** getPullRequestDetails, getPullRequestDiff, analyzeCode, generateReviewSummary, listOpenPullRequests, readLocalFile
- **LLM Providers:** Ollama, OpenAI, LM Studio
- **Platform:** Kotlin + Langchain4j
