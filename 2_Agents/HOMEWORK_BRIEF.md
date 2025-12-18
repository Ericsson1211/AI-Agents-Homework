# Praktické cvičení - Lekce 4
# AI Agenti

---

## Purpose
Automates Android emulator management and APK deployment through natural language interface.

## Diagram
```
User → AI Agent → 7 Tools → API → ADB/Database
```

## Implementation
- **Tools (7):** list_avds, check_devices, start_emulator, stop_emulator, install_apk, save_memory, get_memory
- **Database:** PostgreSQL with 4 tables
- **ADB Bridge: REST API connecting n8n to host Mac's ADB
- **Platform:** n8n + Local LLM (Used Qwen 2.5B 7B)

