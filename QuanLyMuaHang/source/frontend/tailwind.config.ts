import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#14264d',
        accent: '#ed7d16',
        mist: '#f4f6fa',
      },
      boxShadow: {
        soft: '0 12px 35px rgba(20, 38, 77, 0.08)',
      },
    },
  },
  plugins: [],
} satisfies Config
