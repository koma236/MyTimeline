/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // mock/css/style.css の :root と同じ値。プロトタイプと見た目を揃える
      colors: {
        bg: '#ffffff',
        'bg-subtle': '#f7f9f9',
        text: '#0f1419',
        muted: '#536471',
        border: '#eff3f4',
        'border-strong': '#cfd9de',
        accent: '#1d9bf0',
        'accent-hover': '#1a8cd8',
        like: '#f91880',
        danger: '#f4212e',
      },
      fontFamily: {
        sans: [
          '"Hiragino Sans"',
          '"Hiragino Kaku Gothic ProN"',
          '"Yu Gothic"',
          'Meiryo',
          'system-ui',
          '-apple-system',
          'sans-serif',
        ],
      },
      maxWidth: {
        // mock の --column
        column: '620px',
      },
    },
  },
  plugins: [],
}
