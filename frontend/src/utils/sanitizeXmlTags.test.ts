import { describe, it, expect } from 'vitest';
import { sanitizeXmlTags } from './sanitizeXmlTags';

describe('sanitizeXmlTags', () => {
    it('returns empty string for empty input', () => {
        expect(sanitizeXmlTags('')).toBe('');
        // @ts-ignore - testing runtime behaviour
        expect(sanitizeXmlTags(null)).toBe('');
        // @ts-ignore - testing runtime behaviour
        expect(sanitizeXmlTags(undefined)).toBe('');
    });

    it('leaves standard HTML tags unmodified', () => {
        const input = '<b>bold</b> <p>paragraph</p> <a href="#">link</a> <div>div</div>';
        expect(sanitizeXmlTags(input)).toBe(input);
    });

    it('wraps custom tags without underscores with double newlines', () => {
        const input = '<custom>content</custom>';
        const expected = '\n\n<custom>\n\ncontent\n\n</custom>\n\n';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });

    it('replaces underscores with dashes in custom tags and adds data-original-tag', () => {
        const input = '<custom_tag>content</custom_tag>';
        const expected = '\n\n<custom-tag data-original-tag="custom_tag">\n\ncontent\n\n</custom-tag>\n\n';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });

    it('preserves attributes in custom tags with underscores', () => {
        const input = '<my_tag id="1" class="test">content</my_tag>';
        const expected = '\n\n<my-tag data-original-tag="my_tag" id="1" class="test">\n\ncontent\n\n</my-tag>\n\n';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });

    it('preserves attributes in custom tags without underscores', () => {
        const input = '<custom id="1">content</custom>';
        const expected = '\n\n<custom id="1">\n\ncontent\n\n</custom>\n\n';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });

    it('handles self-closing custom tags', () => {
        const input = '<custom_tag />';
        const expected = '\n\n<custom-tag data-original-tag="custom_tag" />\n\n';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });

    it('handles complex mixed content', () => {
        const input = '<p>Start</p><tool_call name="test">args</tool_call><b>End</b>';
        const expected = '<p>Start</p>\n\n<tool-call data-original-tag="tool_call" name="test">\n\nargs\n\n</tool-call>\n\n<b>End</b>';
        expect(sanitizeXmlTags(input)).toBe(expected);
    });
});
