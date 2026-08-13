type IconProps = { size?: number };

export function CartIcon({ size = 20 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="20" r="1" /><circle cx="19" cy="20" r="1" />
      <path d="M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h8.7a2 2 0 0 0 1.9-1.5L22 8H6" />
    </svg>
  );
}

export function ArrowUpRightIcon() {
  return <span aria-hidden="true" className="arrow-icon">↗</span>;
}

export function HeartIcon({ filled = false, size = 18 }: { filled?: boolean; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20.8 4.6c-1.7-1.7-4.4-1.7-6.1 0l-.7.7-.7-.7c-1.7-1.7-4.4-1.7-6.1 0-1.7 1.7-1.7 4.4 0 6.1l6.8 6.8 6.8-6.8c1.7-1.7 1.7-4.4 0-6.1Z" />
    </svg>
  );
}

export function SearchIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" />
    </svg>
  );
}