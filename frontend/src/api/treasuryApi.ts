/**
 * Treasury & transfer API (treasury-service, reached only through the API gateway).
 *
 * Assumes `./client` (frontend shell) default-exports an Axios instance whose `baseURL` already
 * ends in `/api/v1` and which attaches the JWT. Every endpoint returns the standard
 * `{ success, message, data, timestamp }` envelope; these helpers unwrap `data`.
 *
 * @module api/treasuryApi
 */
import type { AxiosError } from 'axios';
import apiClient from './client';

/** Saga status of a ledger row. */
export type TransferStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

/** Standard success envelope used by every Orgwise service. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/** Standard error body used by every Orgwise service. */
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
  details?: Record<string, string>;
}

/** Stable pagination shape returned by treasury-service. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** `GET /treasury` */
export interface TreasuryAccount {
  id: number;
  name: string;
  balance: number;
  currency: string;
  updatedAt: string;
}

/** An approved expense waiting for disbursement (`GET /treasury/disbursable`). */
export interface DisbursableExpense {
  id: number;
  title: string | null;
  amount: number;
  status: string;
  requesterId: number | null;
  requesterName: string | null;
  eventId: number | null;
}

/** One ledger row. */
export interface TransferRecord {
  id: number;
  reference: string;
  expenseId: number;
  eventId: number | null;
  expenseTitle: string | null;
  amount: number;
  recipientId: number | null;
  recipientName: string | null;
  disbursedById: number | null;
  disbursedByUsername: string;
  status: TransferStatus;
  failureReason: string | null;
  revertPending: boolean;
  createdAt: string;
  completedAt: string | null;
}

/** Query parameters for paged ledger lists. */
export interface PageParams {
  page?: number;
  size?: number;
  status?: TransferStatus;
}

/**
 * Fetches the treasury balance (Finance Head, President, Admin).
 * @returns the main treasury account
 */
export async function getTreasury(): Promise<TreasuryAccount> {
  const res = await apiClient.get<ApiResponse<TreasuryAccount>>('/treasury');
  return res.data.data;
}

/**
 * Fetches approved expenses that have not been paid yet (Finance Head).
 * Rejects with HTTP 503 when expense-service is down.
 * @returns expenses that can be disbursed
 */
export async function getDisbursableExpenses(): Promise<DisbursableExpense[]> {
  const res = await apiClient.get<ApiResponse<DisbursableExpense[]>>('/treasury/disbursable');
  return res.data.data;
}

/**
 * Runs the disbursement saga for one approved expense (Finance Head).
 * @param expenseId expense to pay
 * @returns the COMPLETED transfer
 */
export async function disburseExpense(expenseId: number): Promise<TransferRecord> {
  const res = await apiClient.post<ApiResponse<TransferRecord>>('/transfers', { expenseId });
  return res.data.data;
}

/**
 * Lists the whole ledger, newest first (Finance Head, President, Admin).
 * @param params page, size and optional status filter
 * @returns a page of transfers
 */
export async function getTransfers(params: PageParams = {}): Promise<Page<TransferRecord>> {
  const res = await apiClient.get<ApiResponse<Page<TransferRecord>>>('/transfers', {
    params: { page: params.page ?? 0, size: params.size ?? 10, ...(params.status ? { status: params.status } : {}) },
  });
  return res.data.data;
}

/**
 * Lists the transfers paid to the signed-in officer.
 * @param params page and size
 * @returns a page of the caller's transfers
 */
export async function getMyTransfers(params: PageParams = {}): Promise<Page<TransferRecord>> {
  const res = await apiClient.get<ApiResponse<Page<TransferRecord>>>('/transfers/me', {
    params: { page: params.page ?? 0, size: params.size ?? 10 },
  });
  return res.data.data;
}

/** Message shown when a downstream service is unavailable (HTTP 503). */
export const SERVICE_UNAVAILABLE_MESSAGE =
  'A required service is temporarily unavailable. No money was moved; please try again in a moment.';

/** A user-facing description of a failed treasury request. */
export interface TreasuryErrorInfo {
  status: number | null;
  message: string;
  /** Set when the saga was compensated: this transfer is FAILED and the balance was restored. */
  failedTransferReference: string | null;
}

/**
 * Turns an Axios/network error into something the UI can show.
 * @param error anything thrown by the API helpers
 * @returns status, message and (if compensated) the FAILED transfer reference
 */
export function describeTreasuryError(error: unknown): TreasuryErrorInfo {
  const axiosError = error as AxiosError<ApiErrorBody>;
  const status = axiosError?.response?.status ?? null;
  const body = axiosError?.response?.data;
  const reference = body?.details?.transferReference ?? null;
  if (status === null) {
    return { status, message: 'Cannot reach the server. Check your connection and try again.', failedTransferReference: null };
  }
  if (status === 503 && !body?.message) {
    return { status, message: SERVICE_UNAVAILABLE_MESSAGE, failedTransferReference: reference };
  }
  return {
    status,
    message: body?.message || `Request failed (HTTP ${status}).`,
    failedTransferReference: reference,
  };
}

/**
 * Formats an amount as Philippine pesos by default.
 * @param amount numeric amount
 * @param currency ISO-4217 code
 * @returns e.g. "₱1,234.50"
 */
export function formatMoney(amount: number, currency = 'PHP'): string {
  return new Intl.NumberFormat('en-PH', { style: 'currency', currency }).format(amount);
}
