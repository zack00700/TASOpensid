import api from '../plugin/axios';

const BASE = '/ask-ai';

/** Shape returned by the backend's GET /ask-ai. The fields we actually use
 *  on the FAB are loosely typed — the report rendering already lives in
 *  AskAiFab itself. */
export interface AskAiSpec {
  title?: string | null;
  answer?: string | null;
  chart?: {
    type?: string;
    labels?: string[];
    datasets?: { name?: string; data?: number[] }[];
  } | null;
  table?: { columns?: string[]; rows?: (string | number)[][] } | null;
}

export interface AskAiStatus {
  enabled: boolean;
  providers?: { openai?: boolean; anthropic?: boolean };
}

/** Lightweight probe used by AskAiFab on mount to hide itself when the
 *  backend has no LLM key configured. Returns a graceful "disabled" object
 *  on any error so the UI never surfaces a misleading enabled state. */
export const getAskAiStatus = async (): Promise<AskAiStatus> => {
  try {
    const response = await api.get(`${BASE}/status`);
    return response.data as AskAiStatus;
  } catch (error) {
    console.error('[AskAiService] Failed to read status:', error);
    return { enabled: false };
  }
};

/** Submits a natural-language question. The response is the AskAiSpec the
 *  FAB renders into chart + table. On 503 AI_NOT_CONFIGURED the caller is
 *  expected to detect it (axios surfaces the error.response.data) and hide
 *  the FAB. */
export const askAi = async (question: string): Promise<AskAiSpec> => {
  const response = await api.post(BASE, { question });
  return response.data as AskAiSpec;
};

export default {
  getAskAiStatus,
  askAi,
};
