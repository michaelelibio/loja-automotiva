export type VerifyEmailRequest = { token: string };
export type ForgotPasswordRequest = { email: string };
export type ResetPasswordRequest = { token: string; newPassword: string };
export type AccountActionResponse = { message: string };
export type ChangePasswordRequest = { currentPassword: string; newPassword: string };
