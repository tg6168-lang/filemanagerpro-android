import { AIDebugResult } from '../types';
import { getApiUrl } from '../utils/apiConfig';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp?: number;
}

export function getSavedGeminiApiKey(): string {
  if (typeof window === 'undefined') return '';
  return localStorage.getItem('pydroid_gemini_api_key') || '';
}

export function saveGeminiApiKey(key: string): void {
  if (typeof window === 'undefined') return;
  if (key && key.trim()) {
    localStorage.setItem('pydroid_gemini_api_key', key.trim());
  } else {
    localStorage.removeItem('pydroid_gemini_api_key');
  }
}

export function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const key = getSavedGeminiApiKey();
  if (key) {
    headers['x-gemini-api-key'] = key;
  }
  return headers;
}

/**
 * Validates a Gemini API Key via the server-side endpoint /api/gemini/validate
 */
export async function validateGeminiApiKey(apiKey: string): Promise<{
  valid: boolean;
  message?: string;
  error?: string;
  model?: string;
}> {
  const trimmedKey = apiKey.trim();
  if (!trimmedKey) {
    return { valid: false, error: 'Khóa API không được để trống.' };
  }

  const endpoint = getApiUrl('/api/gemini/validate');
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey: trimmedKey }),
  });

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return {
      valid: false,
      error: `Không thể kết nối đến máy chủ xác thực (${response.status}).`,
    };
  }

  const data = await response.json().catch(() => ({}));

  if (!response.ok || !data.valid) {
    return {
      valid: false,
      error: data.error || `Khóa API không hợp lệ hoặc thiếu quyền hạn (HTTP ${response.status}).`,
    };
  }

  return {
    valid: true,
    message: data.message || 'Khóa Gemini API hợp lệ và sẵn sàng sử dụng!',
    model: data.model,
  };
}

/**
 * AI Server Status Ping Check
 * Queries the server-side health & engine endpoint /api/engine/status
 */
export async function checkAIServerStatus(): Promise<{
  online: boolean;
  message: string;
  source: 'server' | 'direct_key' | 'none';
}> {
  try {
    const res = await fetch(getApiUrl('/api/engine/status'), {
      signal: AbortSignal.timeout(6000),
    });
    const ct = res.headers.get('content-type') || '';
    if (res.ok && ct.includes('application/json')) {
      const data = await res.json();
      if (data.status === 'online') {
        return {
          online: true,
          message: 'Máy chủ AI đang hoạt động bình thường',
          source: 'server',
        };
      }
    }
  } catch (e) {}

  return {
    online: false,
    message: 'Máy chủ AI tạm thời không phản hồi',
    source: 'none',
  };
}

/**
 * AI Debugger
 * Proxied securely through server route /api/gemini/debug
 */
export async function requestAIDebug(
  code: string,
  errorOutput: string,
  context?: string
): Promise<AIDebugResult> {
  const response = await fetch(getApiUrl('/api/gemini/debug'), {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ code, errorOutput, context }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Lỗi gọi Gemini AI (${response.status})`);
  }

  const data = await response.json();
  return {
    summary: data.summary || 'Đã phân tích mã nguồn.',
    errorType: data.errorType || 'Syntax / Logic Issue',
    lineNumber: data.lineNumber,
    explanation: data.explanation || 'Không có giải thích chi tiết.',
    fixedCode: data.fixedCode || code,
    suggestions: data.suggestions || [],
  };
}

/**
 * AI Code Explainer
 * Proxied securely through server route /api/gemini/explain
 */
export async function requestAIExplain(
  code: string,
  question?: string
): Promise<string> {
  const response = await fetch(getApiUrl('/api/gemini/explain'), {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ code, question }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Lỗi gọi Gemini AI (${response.status})`);
  }

  const data = await response.json();
  return data.explanation || '';
}

/**
 * AI Chat Assistant (Full text response)
 * Proxied securely through server route /api/gemini/chat
 */
export async function requestAIChat(
  messages: { role: 'user' | 'assistant'; content: string }[],
  currentCode?: string
): Promise<string> {
  const response = await fetch(getApiUrl('/api/gemini/chat'), {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ messages, currentCode }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Lỗi kết nối Trợ lý AI (${response.status})`);
  }

  const data = await response.json();
  return data.reply || 'Xin chào! Tôi có thể giúp gì cho bạn về lập trình Python?';
}

/**
 * AI Chat Streaming Assistant
 * Proxied via Server-Sent Events (SSE) from server route /api/gemini/chat
 */
export async function requestAIChatStream(
  messages: { role: 'user' | 'assistant'; content: string }[],
  onChunk: (chunk: string) => void,
  currentCode?: string
): Promise<string> {
  const headers = getAuthHeaders();
  headers['Accept'] = 'text/event-stream';

  const response = await fetch(getApiUrl('/api/gemini/chat'), {
    method: 'POST',
    headers,
    body: JSON.stringify({ messages, currentCode, stream: true }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Lỗi kết nối Trợ lý AI (${response.status})`);
  }

  if (response.body) {
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let accumulated = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;
        const jsonStr = trimmed.replace(/^data:\s*/, '');
        try {
          const payload = JSON.parse(jsonStr);
          if (payload.chunk) {
            accumulated += payload.chunk;
            onChunk(payload.chunk);
          } else if (payload.error) {
            throw new Error(payload.error);
          }
        } catch (e: any) {
          if (e.message && e.message.includes('Lỗi')) throw e;
        }
      }
    }
    return accumulated;
  }

  const data = await response.json();
  const reply = data.reply || '';
  onChunk(reply);
  return reply;
}


