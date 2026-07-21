import { describe, it, expect } from 'vitest';
import { parseLlamaResponse, parseMarkdownChat } from '../chatParser';

describe('chatParser', () => {
    describe('parseLlamaResponse', () => {
        it('should handle basic non-streaming JSON payload', () => {
            const resStr = JSON.stringify({
                choices: [
                    {
                        message: {
                            role: 'assistant',
                            content: 'Hello there!'
                        }
                    }
                ]
            });
            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({ role: 'assistant', content: 'Hello there!' });
        });

        it('should handle streaming SSE payload with delta content', () => {
            const resStr = [
                'data: {"choices":[{"delta":{"content":"Hello"}}]}',
                'data: {"choices":[{"delta":{"content":" "}}]}',
                'data: {"choices":[{"delta":{"content":"world!"}}]}',
                'data: [DONE]'
            ].join('\n');
            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({ role: 'assistant', content: 'Hello world!' });
        });

        it('should ignore data: [DONE]', () => {
            const resStr = [
                'data: {"choices":[{"delta":{"content":"Hello"}}]}',
                'data: [DONE]'
            ].join('\n');
            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({ role: 'assistant', content: 'Hello' });
        });

        it('should handle streaming tool calls', () => {
            const resStr = [
                'data: {"choices":[{"delta":{"content":""}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_123","type":"function","function":{"name":"get_weather","arguments":""}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"lo"}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"cation\\": \\"Tokyo\\""}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}}]}}]}',
                'data: [DONE]'
            ].join('\n');

            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({
                role: 'assistant',
                content: '',
                tool_calls: [
                    {
                        id: 'call_123',
                        type: 'function',
                        function: {
                            name: 'get_weather',
                            arguments: '{"location": "Tokyo"}'
                        }
                    }
                ]
            });
        });

        it('should handle multiple tool calls streaming simultaneously', () => {
            const resStr = [
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_weather","arguments":""}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_2","type":"function","function":{"name":"get_time","arguments":""}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"loc\\":\\"NY\\"}"}}]}}]}',
                'data: {"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{}"}}]}}]}',
                'data: [DONE]'
            ].join('\n');

            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({
                role: 'assistant',
                content: '',
                tool_calls: [
                    {
                        id: 'call_1',
                        type: 'function',
                        function: {
                            name: 'get_weather',
                            arguments: '{"loc":"NY"}'
                        }
                    },
                    {
                        id: 'call_2',
                        type: 'function',
                        function: {
                            name: 'get_time',
                            arguments: '{}'
                        }
                    }
                ]
            });
        });

        it('should gracefully handle invalid JSON in streaming payload without crashing', () => {
            const resStr = [
                'data: {"choices":[{"delta":{"content":"Start"}}]}',
                'data: {"invalid JSON here...',
                'data: {"choices":[{"delta":{"content":" End"}}]}',
                'data: [DONE]'
            ].join('\n');
            const result = parseLlamaResponse(resStr);
            expect(result).toEqual({ role: 'assistant', content: 'Start End' });
        });

        it('should gracefully handle invalid JSON in non-streaming payload', () => {
            const resStr = '{"invalid JSON here...';
            const result = parseLlamaResponse(resStr);
            expect(result).toBeNull();
        });

        it('should return null for unexpected non-streaming JSON structure', () => {
            const resStr = JSON.stringify({ unexpected: true });
            const result = parseLlamaResponse(resStr);
            expect(result).toBeNull();
        });
    });

    describe('parseMarkdownChat', () => {
        it('should return empty array for empty payload', () => {
            expect(parseMarkdownChat('')).toEqual([]);
        });

        it('should return empty array if format is missing RESPONSE section', () => {
            const payload = 'REQUEST:\n{"messages":[{"role":"user","content":"hi"}]}';
            expect(parseMarkdownChat(payload)).toEqual([]);
        });

        it('should parse request messages and response', () => {
            const reqMessages = [{ role: 'user', content: 'hello' }];
            const resObj = { choices: [{ message: { role: 'assistant', content: 'hi there' } }] };
            const payload = `REQUEST:\n{"messages":${JSON.stringify(reqMessages)}}\nRESPONSE:\n${JSON.stringify(resObj)}`;

            const result = parseMarkdownChat(payload);
            expect(result).toEqual([
                { role: 'user', content: 'hello' },
                { role: 'assistant', content: 'hi there' }
            ]);
        });

        it('should handle invalid JSON in REQUEST gracefully', () => {
            const resObj = { choices: [{ message: { role: 'assistant', content: 'hi there' } }] };
            const payload = `REQUEST:\n{"invalid_json\nRESPONSE:\n${JSON.stringify(resObj)}`;

            const result = parseMarkdownChat(payload);
            expect(result).toEqual([
                { role: 'assistant', content: 'hi there' }
            ]);
        });
    });
});
