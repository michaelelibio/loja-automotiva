import type { Category } from '@/lib/catalog-types';

export const CATEGORY_ILLUSTRATIONS = {
  lavagem: '/categories/lavagem.svg',
  protecao: '/categories/protecao.svg',
  detalhamento: '/categories/detalhamento.svg',
  polimento: '/categories/polimento.svg',
  internos: '/categories/cuidados-internos.svg',
  externos: '/categories/cuidados-externos.svg',
  ferramentas: '/categories/ferramentas.svg',
  acessorios: '/categories/acessorios.svg',
} as const;

type CategoryIdentity = Pick<Category, 'name' | 'slug' | 'path'>;

/** Store-owned artwork follows the category identity, including future categories. */
export function categoryIllustration(category: CategoryIdentity): string {
  const identity = `${category.name} ${category.slug} ${category.path}`
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  if (/interior|intern[oa]|couro|estofad|painel/.test(identity)) return CATEGORY_ILLUSTRATIONS.internos;
  if (/exterior|extern[oa]|pneu|roda|vidro/.test(identity)) return CATEGORY_ILLUSTRATIONS.externos;
  if (/polimento|polir|politriz|lustro/.test(identity)) return CATEGORY_ILLUSTRATIONS.polimento;
  if (/protec|cera|selante|ceramic|vitrifica/.test(identity)) return CATEGORY_ILLUSTRATIONS.protecao;
  if (/detalh|pincel|acabamento/.test(identity)) return CATEGORY_ILLUSTRATIONS.detalhamento;
  if (/lavagem|lavar|shampoo|espuma|limpeza/.test(identity)) return CATEGORY_ILLUSTRATIONS.lavagem;
  if (/ferrament|equipamento|maquina/.test(identity)) return CATEGORY_ILLUSTRATIONS.ferramentas;
  return CATEGORY_ILLUSTRATIONS.acessorios;
}

/** Preserve supplied media without accepting executable or protocol-relative URLs. */
export function categoryImageSource(category: CategoryIdentity & { imageUrl?: string | null }): string {
  const source = category.imageUrl?.trim();
  if (source?.startsWith('/') && !source.startsWith('//') && !source.includes('\\')) return source;
  if (source) {
    try {
      const url = new URL(source);
      if (url.protocol === 'https:' && !url.username && !url.password) return url.href;
    } catch { /* Invalid supplied media uses the store-owned illustration. */ }
  }
  return categoryIllustration(category);
}
