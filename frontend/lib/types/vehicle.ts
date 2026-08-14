export type Vehicle = {
  id: number;
  brand: string;
  model: string;
  year: number;
  version: string | null;
  licensePlate: string | null;
  isPrimary: boolean;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
};

export type VehicleRequest = {
  brand: string;
  model: string;
  year: number;
  version?: string;
  licensePlate?: string;
  isPrimary?: boolean;
  imageUrl?: string | null;
};
