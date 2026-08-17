'use client';

import Image from 'next/image';
import { useState } from 'react';

export function AdminVehicleImage({ src, alt }: { src: string | null; alt: string }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (!src || failedSrc === src) return <span className="admin-vehicle-image-fallback" aria-label="Imagem do veículo indisponível">G</span>;
  return <Image className="admin-vehicle-image" src={src} alt={alt} width={180} height={110} unoptimized onError={() => setFailedSrc(src)} />;
}
