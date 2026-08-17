'use client';

import Image from 'next/image';
import { useState } from 'react';

export function AdminProductImage({ src, alt, size = 64 }: { src: string | null; alt: string; size?: number }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (!src || failedSrc === src) return <span className="admin-product-image-fallback" style={{ width: size, height: size }} aria-label="Imagem indisponível">G</span>;
  return <Image className="admin-product-image" src={src} alt={alt} width={size} height={size} unoptimized onError={() => setFailedSrc(src)} />;
}
