export type Product = {
  id: string;
  name: string;
  category: 'Lavagem' | 'Proteção' | 'Detalhamento' | 'Acessórios';
  price: number;
  oldPrice?: number;
  accent: string;
  image: string;
  featured?: boolean;
};

export const products: Product[] = [
  { id: 'shampoo-neutro-pro', name: 'Shampoo Neutro Pro', category: 'Lavagem', price: 49.9, accent: '#b5c4c3', image: 'https://images.unsplash.com/photo-1607860108855-64acf2078ed9?auto=format&fit=crop&w=900&q=85', featured: true },
  { id: 'cera-sintetica-ultra', name: 'Cera Sintética Ultra', category: 'Proteção', price: 89.9, oldPrice: 109.9, accent: '#c8b99d', image: 'https://images.unsplash.com/photo-1625047509248-ec889cbff17f?auto=format&fit=crop&w=900&q=85', featured: true },
  { id: 'kit-pinceis-detalhamento', name: 'Kit Pincéis Detalhamento', category: 'Detalhamento', price: 74.9, accent: '#a9b0bc', image: 'https://images.unsplash.com/photo-1565689876697-52c4f4c8c1ad?auto=format&fit=crop&w=900&q=85', featured: true },
  { id: 'toalha-secagem-1200', name: 'Toalha de Secagem 1200', category: 'Acessórios', price: 39.9, accent: '#b8b8a2', image: 'https://images.unsplash.com/photo-1600706432502-77a0e2e3279c?auto=format&fit=crop&w=900&q=85', featured: true },
  { id: 'espuma-ativa-max', name: 'Espuma Ativa Max', category: 'Lavagem', price: 64.9, accent: '#a8babb', image: 'https://images.unsplash.com/photo-1520340356584-f9917d1eea6f?auto=format&fit=crop&w=900&q=85' },
  { id: 'selante-ceramico', name: 'Selante Cerâmico 9H', category: 'Proteção', price: 129.9, oldPrice: 149.9, accent: '#c1b6aa', image: 'https://images.unsplash.com/photo-1615906655593-ad0386982a0f?auto=format&fit=crop&w=900&q=85' },
  { id: 'luva-microfibra', name: 'Luva de Microfibra Duo', category: 'Detalhamento', price: 34.9, accent: '#aeb4bd', image: 'https://images.unsplash.com/photo-1607860108855-64acf2078ed9?auto=format&fit=crop&w=900&q=85' },
  { id: 'aplicador-foam', name: 'Aplicador Foam Pro', category: 'Acessórios', price: 24.9, accent: '#bbbca9', image: 'https://images.unsplash.com/photo-1600706432502-77a0e2e3279c?auto=format&fit=crop&w=900&q=85' },
];