import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{js,ts,jsx,tsx,mdx}', './components/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        ink: '#111316',
        steel: '#66717d',
        signal: '#e7ff3f',
      },
      fontFamily: {
        display: ['var(--font-space-grotesk)'],
        sans: ['var(--font-manrope)'],
      },
    },
  },
  plugins: [],
};

export default config;