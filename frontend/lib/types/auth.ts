export type User = {
  id: number;
  name: string;
  email: string;
  pictureUrl?: string | null;
  authProvider?: string | null;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = {
  name: string;
  email: string;
  password: string;
};

export type UpdateUserRequest = {
  name: string;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn?: number;
  user: User;
};
