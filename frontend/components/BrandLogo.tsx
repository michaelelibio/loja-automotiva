type BrandLogoProps = {
  variant?: 'light' | 'dark' | 'mark';
  className?: string;
};

export function BrandLogo({ variant = 'dark', className = '' }: BrandLogoProps) {
  return <span className={`brand-logo brand-logo--${variant} ${className}`} role="img" aria-label="inGarage" />;
}
