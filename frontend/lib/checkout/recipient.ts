export type AliExpressRecipientInput = { cpf: string; birthDate: string; phone: string };
export type RecipientErrors = Partial<Record<'cpf' | 'birthDate' | 'phone' | 'address', string>>;
const ddds = new Set('11 12 13 14 15 16 17 18 19 21 22 24 27 28 31 32 33 34 35 37 38 41 42 43 44 45 46 47 48 49 51 53 54 55 61 62 63 64 65 66 67 68 69 71 73 74 75 77 79 81 82 83 84 85 86 87 88 89 91 92 93 94 95 96 97 98 99'.split(' '));
const ufs = new Set('AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' '));
export const emptyRecipient: AliExpressRecipientInput = { cpf: '', birthDate: '', phone: '' };
export function maskCpf(value: string) {
  return value.replace(/\D/g, '').slice(0, 11).replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/(\d{3})(\d{1,2})$/, '$1-$2');
}
export function maskBirthDate(value: string) {
  return value.replace(/\D/g, '').slice(0, 8).replace(/^(\d{2})(\d)/, '$1/$2').replace(/^(\d{2})\/(\d{2})(\d)/, '$1/$2/$3');
}
export function maskPhone(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 2) return digits ? `(${digits}` : '';
  const local = digits.slice(2); const split = local.length > 8 ? 5 : 4;
  return `(${digits.slice(0, 2)}) ${local.slice(0, split)}${local.length > split ? `-${local.slice(split)}` : ''}`;
}
export function cpfValid(value: string) {
  if (!/^(?:\d{11}|\d{3}\.\d{3}\.\d{3}-\d{2})$/.test(value.trim())) return false;
  const digits = value.replace(/\D/g, '');
  if (/^(\d)\1{10}$/.test(digits)) return false;
  for (const size of [9, 10]) {
    let sum = 0; for (let index = 0; index < size; index++) sum += Number(digits[index]) * (size + 1 - index);
    const digit = ((sum * 10) % 11) % 10;
    if (digit !== Number(digits[size])) return false;
  }
  return true;
}
function todayInBrazil() {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  return ['year', 'month', 'day'].map((type) => parts.find((part) => part.type === type)?.value).join('-');
}
export function birthDateValid(value: string, today = todayInBrazil()) {
  if (!/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return false;
  const [day, month, year] = value.split('/').map(Number); const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return false;
  const canonical = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  const earliest = `${Number(today.slice(0, 4)) - 120}${today.slice(4)}`;
  return canonical < today && canonical >= earliest;
}
export function validateRecipient(input: AliExpressRecipientInput, address: { recipientName: string; zipCode: string; street: string; number: string; neighborhood: string; city: string; state: string } | null): RecipientErrors {
  const errors: RecipientErrors = {};
  if (!cpfValid(input.cpf)) errors.cpf = 'Informe um CPF válido do destinatário.';
  if (!birthDateValid(input.birthDate)) errors.birthDate = 'Informe uma data válida no formato DD/MM/AAAA.';
  const digits = input.phone.replace(/\D/g, '');
  if (!/^[\d() -]{10,20}$/.test(input.phone) || !ddds.has(digits.slice(0, 2)) || !/^(?:[2-5]\d{7}|9\d{8})$/.test(digits.slice(2))) errors.phone = 'Informe um telefone válido com DDD.';
  if (!address || address.recipientName.trim().length > 150 || address.recipientName.trim().split(/\s+/).length < 2
    || !/^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u.test(address.recipientName.trim()) || !/^\d{8}$/.test(address.zipCode)
    || !address.street.trim() || !address.number.trim() || !address.neighborhood.trim() || !address.city.trim() || !ufs.has(address.state)) errors.address = 'Confira o nome completo e os campos do endereço selecionado em Gerenciar endereços.';
  return errors;
}
