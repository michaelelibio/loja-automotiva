import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{js,ts,jsx,tsx,mdx}', './components/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        ink: '#111111',
        steel: '#666666',
        signal: '#ff4d0a',
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
