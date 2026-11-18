'use client';

import Image from 'next/image';
import { useState } from 'react';
import type { Category } from '@/lib/catalog-types';
import { categoryIllustration, categoryImageSource } from '@/lib/category-images';

export function CategoryImage({ category }: { category: Category }) {
  const source = categoryImageSource(category);
  const fallback = categoryIllustration(category);
  const [failedSources, setFailedSources] = useState<string[]>([]);
  const currentSource = failedSources.includes(source) ? fallback : source;

  return <>
    {/* This inline illustration remains visible even if all image requests fail. */}
    <svg className="home-category__emergency-art" viewBox="0 0 640 480" fill="none" aria-hidden="true">
      <circle cx="345" cy="215" r="150" fill="#f4dfca" />
      <ellipse cx="320" cy="343" rx="180" ry="20" fill="#c6b5a4" />
      <rect x="229" y="156" width="110" height="180" rx="23" fill="#34383b" />
      <path d="M256 128h58v34h-58zM268 98h34v33h-34z" fill="#ff690b" />
      <path d="M250 220h68v66h-68z" fill="#ff690b" />
      <path d="m360 236 92-22 23 99-92 22z" fill="#f2833b" />
      <path d="m369 254 80-19m-75 42 80-19m-75 42 80-19" stroke="#ffc191" strokeWidth="5" />
    </svg>
    {!failedSources.includes(currentSource) && <Image
      src={currentSource} alt="" fill sizes="(max-width: 350px) 90vw, (max-width: 900px) 45vw, 23vw"
      loading="lazy" decoding="async" unoptimized
      onError={() => setFailedSources((previous) => previous.includes(currentSource) ? previous : [...previous, currentSource])}
    />}
  </>;
}
