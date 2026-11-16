import { CatalogPage } from '@/components/CatalogPage';
export default async function CategoryPage({ params, searchParams }: { params: Promise<{ path: string[] }>; searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const { path } = await params;
  return <CatalogPage categoryPath={path.join('/')} raw={await searchParams} />;
}
