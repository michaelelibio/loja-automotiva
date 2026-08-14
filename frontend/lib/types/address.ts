export type Address = {
  id: number;
  label: string | null;
  recipientName: string;
  zipCode: string;
  street: string;
  number: string;
  complement: string | null;
  neighborhood: string;
  city: string;
  state: string;
  isPrimary: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AddressRequest = {
  label?: string;
  recipientName: string;
  zipCode: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
  isPrimary?: boolean;
};
