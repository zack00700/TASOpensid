// Client-side HTML renderer for instant preview
// Generates HTML string representing the provided template and data

export interface Template {
  pages: Page[];
}

export interface Page {
  frames: Frame[];
}

export type Frame = TextFrame | ImageFrame | TableFrame;

interface BaseFrame {
  x: number; // mm
  y: number; // mm
  w: number; // mm
  h: number; // mm
  rotation?: number; // deg
  z?: number;
}

export interface TextFrame extends BaseFrame {
  type: 'text';
  /** Static text content */
  text?: string;
  /** Data binding path */
  binding?: string;
}

export interface ImageFrame extends BaseFrame {
  type: 'image';
  /** Static URL for the image */
  url?: string;
  /** Binding path for the image URL */
  binding?: string;
}

export interface TableFrame extends BaseFrame {
  type: 'table';
  /** Column definitions */
  columns: TableColumn[];
}

export interface TableColumn {
  header?: string;
  /** binding path within a line */
  binding: string;
  /** column width in percent */
  width: number; // percent
  /** Optional formatter: "number" or "date" */
  format?: string;
  /** Aggregate expression for totals, e.g. "sum" or "sum(amount)" */
  total?: string;
}

/**
 * Resolve a value from an object using a dot separated path.  Returns empty
 * string when the value is not present.
 */
function resolve(obj: any, path?: string): any {
  if (!path) return '';
  const parts = path.split('.');
  let cur = obj;
  for (const p of parts) {
    if (cur == null) return '';
    cur = cur[p];
  }
  return cur == null ? '' : cur;
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Simple value formatter supporting numbers and dates
function formatValue(value: any, fmt?: string): string {
  if (value == null || value === '') return '';
  if (!fmt) {
    if (typeof value === 'number') return value.toString();
    return String(value);
  }
  switch (fmt) {
    case 'number':
      return typeof value === 'number'
        ? value.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })
        : String(value);
    case 'date':
      try {
        const d = new Date(value);
        if (!isNaN(d.getTime())) {
          return d.toLocaleDateString('en-US');
        }
      } catch {
        /* noop */
      }
      return String(value);
    default:
      return String(value);
  }
}

// Fallback 1x1 transparent pixel
const FALLBACK_IMG =
  'data:image/gif;base64,R0lGODlhAQABAAAAACwAAAAAAQABAAA=';

function frameStyle(f: BaseFrame): string {
  const parts: string[] = [];
  parts.push('position:absolute');
  parts.push(`left:${f.x}mm`);
  parts.push(`top:${f.y}mm`);
  parts.push(`width:${f.w}mm`);
  parts.push(`height:${f.h}mm`);
  parts.push('overflow:hidden');
  parts.push('text-overflow:ellipsis');
  parts.push('white-space:nowrap');
  if (f.rotation) {
    parts.push(`transform:rotate(${f.rotation}deg)`);
  }
  if (f.z != null) {
    parts.push(`z-index:${f.z}`);
  }
  return parts.join(';');
}

function parseTotal(expr: string | undefined, binding: string): {
  op: 'sum' | 'min' | 'max';
  field: string;
} | null {
  if (!expr) return null;
  const simple = /^(sum|min|max)$/.exec(expr);
  if (simple) {
    return { op: simple[1] as any, field: binding };
  }
  const m = /^(sum|min|max)\(([^)]+)\)$/.exec(expr);
  if (m) {
    return { op: m[1] as any, field: m[2] };
  }
  return null;
}

function renderText(frame: TextFrame, data: any): string {
  const value =
    frame.binding !== undefined && frame.binding !== ''
      ? resolve(data, frame.binding)
      : frame.text ?? '';
  const content = value == null ? '' : String(value);
  return `<div class="ff-frame ff-text" style="${frameStyle(frame)}">${escapeHtml(
    content
  )}</div>`;
}

function renderImage(frame: ImageFrame, data: any): string {
  const urlRaw =
    frame.binding !== undefined && frame.binding !== ''
      ? resolve(data, frame.binding)
      : frame.url ?? '';
  const url = urlRaw ? String(urlRaw) : '';
  return `<img class="ff-frame ff-image" src="${url}" onerror="this.onerror=null;this.src='${FALLBACK_IMG}';" style="${frameStyle(
    frame
  )};object-fit:contain;" />`;
}

function renderTable(frame: TableFrame, data: any): string {
  const lines: any[] = Array.isArray((data as any).lines)
    ? (data as any).lines
    : [];

  const headers = frame.columns
    .map(
      (c) =>
        `<th style="width:${c.width}%;text-align:left;">${escapeHtml(
          c.header || ''
        )}</th>`
    )
    .join('');

  const rows = lines
    .map((line) => {
      const tds = frame.columns
        .map((c) =>
          `<td style="width:${c.width}%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(
            formatValue(resolve(line, c.binding), c.format)
          )}</td>`
        )
        .join('');
      return `<tr>${tds}</tr>`;
    })
    .join('');

  const totals = frame.columns.map((c) => parseTotal(c.total, c.binding));
  let totalRow = '';
  if (totals.some((t) => t !== null)) {
    const values = totals.map((t, idx) => {
      if (!t) return '';
      const nums = lines
        .map((line) => resolve(line, t.field))
        .map((v) => (typeof v === 'number' ? v : parseFloat(v)))
        .filter((v) => !isNaN(v));
      if (!nums.length) return '';
      let agg: number;
      switch (t.op) {
        case 'sum':
          agg = nums.reduce((a, b) => a + b, 0);
          break;
        case 'min':
          agg = Math.min(...nums);
          break;
        case 'max':
          agg = Math.max(...nums);
          break;
      }
      return escapeHtml(formatValue(agg, frame.columns[idx].format));
    });
    const tds = values
      .map(
        (v, i) =>
          `<td style="width:${frame.columns[i].width}%;font-weight:bold;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${v}</td>`
      )
      .join('');
    totalRow = `<tfoot><tr>${tds}</tr></tfoot>`;
  }

  return `<table class="ff-table" style="width:100%;border-collapse:collapse;">` +
    `<thead><tr>${headers}</tr></thead>` +
    `<tbody>${rows}</tbody>` +
    `${totalRow}</table>`;
}

export function htmlRenderer(template: Template, data: any): string {
  const pagesHtml = template.pages
    .map((page) => {
      const framesHtml = page.frames
        .map((frame) => {
          switch (frame.type) {
            case 'text':
              return renderText(frame, data);
            case 'image':
              return renderImage(frame, data);
            case 'table':
              return renderTable(frame, data);
            default:
              return '';
          }
        })
        .join('');
      return `<div class="ff-page" style="position:relative;width:210mm;height:297mm;">${framesHtml}</div>`;
    })
    .join('');

  return pagesHtml;
}

export default htmlRenderer;
