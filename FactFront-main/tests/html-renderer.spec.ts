import { describe, it, expect } from 'vitest';
import { htmlRenderer, Template } from '../src/utils/htmlRenderer';

describe('htmlRenderer', () => {
  it('renders page wrapper and resolves bindings', () => {
    const template: Template = {
      pages: [
        {
          frames: [
            { type: 'text', x: 10, y: 20, w: 50, h: 10, binding: 'customer.name' },
          ],
        },
      ],
    };
    const data = { customer: { name: 'Acme Corp' } };
    const html = htmlRenderer(template, data);
    expect(html).toContain('position:relative;width:210mm;height:297mm');
    expect(html).toContain('Acme Corp');
    // ensure overflow style applied
    expect(html).toMatch(/overflow:hidden/);
  });

  it('safely handles missing bindings', () => {
    const template: Template = {
      pages: [
        {
          frames: [
            { type: 'text', x: 0, y: 0, w: 10, h: 10, binding: 'foo.bar' },
          ],
        },
      ],
    };
    const html = htmlRenderer(template, {});
    // should render empty string for missing binding
    expect(html).not.toContain('undefined');
    expect(html).toMatch(/<div[^>]*><\/div>/);
  });

  it('renders images with fallback', () => {
    const template: Template = {
      pages: [
        { frames: [ { type: 'image', x: 0, y: 0, w: 10, h: 10, binding: 'logo' } ] },
      ],
    };
    const html = htmlRenderer(template, { logo: 'http://example.com/logo.png' });
    expect(html).toContain('http://example.com/logo.png');
    expect(html).toContain('onerror="this.onerror=null;this.src');
  });

  it('renders tables with formatting and totals', () => {
    const template: Template = {
      pages: [
        {
          frames: [
            {
              type: 'table',
              x: 0,
              y: 0,
              w: 100,
              h: 50,
              columns: [
                { header: 'Desc', binding: 'description', width: 50 },
                {
                  header: 'Amount',
                  binding: 'amount',
                  width: 50,
                  format: 'number',
                  total: 'sum',
                },
              ],
            },
          ],
        },
      ],
    };
    const data = {
      lines: [
        { description: 'A', amount: 1 },
        { description: 'B', amount: 2.5 },
      ],
    };
    const html = htmlRenderer(template, data);
    // column widths
    expect(html).toContain('width:50%');
    // formatted numbers
    expect(html).toContain('1.00');
    expect(html).toContain('2.50');
    // totals row
    expect(html).toContain('3.50');
  });
});
