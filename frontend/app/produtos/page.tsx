import { CatalogPage } from '@/components/CatalogPage';
export default async function ProductsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  return <CatalogPage raw={await searchParams} />;
}
