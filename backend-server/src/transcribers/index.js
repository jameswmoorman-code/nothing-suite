import { config } from '../config.js';
import { OpenAITranscriber } from './openaiTranscriber.js';
import { LocalWhisperTranscriber } from './localWhisperTranscriber.js';

/**
 * Every transcriber implements:
 *   connect(): Promise<void>
 *   ready: boolean
 *   appendUlawBase64(payload: string): void
 *   close(): void
 * and reports text via { onDelta, onFinal, onError }.
 */
export function createTranscriber(handlers) {
  return config.transcriber === 'openai'
    ? new OpenAITranscriber(handlers)
    : new LocalWhisperTranscriber(handlers);
}
