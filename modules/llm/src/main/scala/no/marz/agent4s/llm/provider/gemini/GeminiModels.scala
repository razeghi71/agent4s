package no.marz.agent4s.llm.provider.gemini

// Gemini's OpenAI-compatible endpoint returns vanilla OpenAI format.
// No custom response types needed -- we use OpenAIChatResponse / OpenAIUsage directly.
// This file exists for consistency with other providers; add Gemini-specific
// response fields here if the API diverges in the future.

object GeminiModels:
  // Re-export OpenAI codecs so provider code can do a single import
  export no.marz.agent4s.llm.provider.openai.OpenAIModels.given
