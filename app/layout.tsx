import type { Metadata } from 'next';
import { Manrope, Space_Grotesk } from 'next/font/google';
import { CartProvider } from '@/context/CartContext';
import './globals.css';

const manrope = Manrope({ subsets: ['latin'], variable: '--font-manrope' });
const spaceGrotesk = Space_Grotesk({ subsets: ['latin'], variable: '--font-space-grotesk' });

export const metadata: Metadata = {
  title: 'GARAGE — Cuidado automotivo',
  description: 'Produtos selecionados para cuidar do seu carro.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body className={`${manrope.variable} ${spaceGrotesk.variable}`}><CartProvider>{children}</CartProvider></body>
    </html>
  );
}