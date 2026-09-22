# Aria Reply V26

Dark pastel clay redesign and context-aware API diagnostics.

## UI architecture
- Command Center dashboard
- Dedicated Diagnostics page
- Dedicated Chats / Context page
- Chat detail viewer
- Dedicated API Test Lab with sender + message input
- Settings with persistent provider state
- System page with architecture and feature inventory
- Five-item bottom navigation
- Dark cocoa background, mocha surfaces, sage/teal active states, terracotta warnings
- XML/vector icons; no white canvas, bento grid, or elevation shadows

## AI
- Groq and Gemini providers
- Groq default model: `openai/gpt-oss-120b`
- Groq endpoint: `https://api.groq.com/openai/v1/chat/completions`
- Gemini API support
- API keys encrypted with Android Keystore
- Dedicated manual API Test Lab
- Detailed HTTP/error-body diagnostics

## Context
- Captures sender/message pairs from WhatsApp notifications and synthetic tests
- Stores up to 200 local message records
- Shows conversations and full local chat detail
- Sends the latest 10 messages for the same sender as conversational context to the AI engine
- Context is never sent when the test is run without context selection

## Automation
- Notification capture
- RemoteInput detection
- Synthetic Aria notification with real RemoteInput action
- AI generation
- Delayed reply delivery
- Direct RemoteInput test
- Event log and runtime diagnostics
