/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        // Tide: Inter porte le corps de texte, Instrument Serif les titres de page,
        // JetBrains Mono les données et libellés techniques.
        sans: ['Inter', '"Plus Jakarta Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
        display: ['"Instrument Serif"', 'Georgia', 'ui-serif', 'serif'],
      },
      letterSpacing: {
        display: '-0.025em',
        heading: '-0.015em',
      },
      colors: {
        // ── Tide ────────────────────────────────────────────────────────────
        // Palette du design Tide (projet Claude Design « tas »). Ajoutée à côté
        // de `brand`, qui reste utilisé par les écrans pas encore repris.
        tide: {
          sand: '#ede5d4',        // fond de page
          'sand-light': '#f5efe1',
          'sand-deep': '#ece4d3',
          paper: '#fdfaf2',       // texte sur fond bleu
          ink: '#2a241e',         // encre principale
          blue: '#5a8aab',
          'blue-deep': '#3e6080',
          'blue-btn': '#6b94b3',
          'blue-btn-deep': '#4a7593',
          green: '#65997b',
          'green-deep': '#5e8a6b',
          amber: '#b8862e',
          'amber-deep': '#9a6f24',
          rust: '#b56358',
          'rust-deep': '#9b4f47',
        },
        brand: {
          50:  '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          900: '#1e3a8a',
        },
      },
      borderRadius: {
        // Tokens historiques — ne pas modifier sans mesurer l'impact (CLAUDE_front.md).
        btn: '8px',
        card: '12px',
        // Rayons Tide.
        'tide-btn': '14px',
        'tide-card': '18px',
        'tide-pill': '10px',
      },
      boxShadow: {
        glass: '0 1px 0 rgba(255,255,255,0.80) inset, 0 1px 2px rgba(60,50,35,0.04), 0 20px 50px -22px rgba(60,50,35,0.18)',
        'glass-deep': '0 1px 0 rgba(255,255,255,0.80) inset, 0 1px 2px rgba(60,50,35,0.05), 0 24px 60px -22px rgba(60,50,35,0.22)',
      },
    },
  },
  plugins: [],
}
