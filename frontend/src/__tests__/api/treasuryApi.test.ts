import { describe, it, expect, vi, beforeEach } from 'vitest';
import apiClient from '../../api/client';
import {
  SERVICE_UNAVAILABLE_MESSAGE,
  describeTreasuryError,
  disburseExpense,
  formatMoney,
  getDisbursableExpenses,
  getMyTransfers,
  getTransfers,
  getTreasury,
} from '../../api/treasuryApi';

vi.mock('../../api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));

const client = vi.mocked(apiClient, true);
const envelope = <T,>(data: T) => ({ data: { success: true, message: 'ok', data, timestamp: '' } });

beforeEach(() => vi.resetAllMocks());

describe('treasuryApi requests', () => {
  it('FE-API-01 getTreasury GETs /treasury and unwraps data', async () => {
    client.get.mockResolvedValue(envelope({ id: 1, balance: 10 }));
    await expect(getTreasury()).resolves.toEqual({ id: 1, balance: 10 });
    expect(client.get).toHaveBeenCalledWith('/treasury');
  });

  it('FE-API-02 getDisbursableExpenses GETs /treasury/disbursable', async () => {
    client.get.mockResolvedValue(envelope([{ id: 5 }]));
    await expect(getDisbursableExpenses()).resolves.toEqual([{ id: 5 }]);
    expect(client.get).toHaveBeenCalledWith('/treasury/disbursable');
  });

  it('FE-API-03 disburseExpense POSTs the expenseId to /transfers', async () => {
    client.post.mockResolvedValue(envelope({ reference: 'r' }));
    await expect(disburseExpense(5)).resolves.toEqual({ reference: 'r' });
    expect(client.post).toHaveBeenCalledWith('/transfers', { expenseId: 5 });
  });

  it('FE-API-04 getTransfers sends defaults and an optional status', async () => {
    client.get.mockResolvedValue(envelope({ content: [] }));
    await getTransfers();
    expect(client.get).toHaveBeenLastCalledWith('/transfers', { params: { page: 0, size: 10 } });
    await getTransfers({ page: 2, size: 5, status: 'FAILED' });
    expect(client.get).toHaveBeenLastCalledWith('/transfers', { params: { page: 2, size: 5, status: 'FAILED' } });
  });

  it('FE-API-05 getMyTransfers GETs /transfers/me with paging', async () => {
    client.get.mockResolvedValue(envelope({ content: [] }));
    await getMyTransfers({ page: 1 });
    expect(client.get).toHaveBeenCalledWith('/transfers/me', { params: { page: 1, size: 10 } });
  });

  it('FE-API-06 errors from the client propagate', async () => {
    client.post.mockRejectedValue(new Error('boom'));
    await expect(disburseExpense(1)).rejects.toThrow('boom');
  });
});

describe('describeTreasuryError', () => {
  it('FE-API-07 uses the server message and the FAILED transfer reference', () => {
    const info = describeTreasuryError({ response: { status: 503, data: { status: 503, message: 'restored', details: { transferReference: 'ref-1' } } } });
    expect(info).toEqual({ status: 503, message: 'restored', failedTransferReference: 'ref-1' });
  });

  it('FE-API-08 falls back to a friendly 503 message or a generic HTTP message', () => {
    expect(describeTreasuryError({ response: { status: 503, data: {} } }).message).toBe(SERVICE_UNAVAILABLE_MESSAGE);
    expect(describeTreasuryError({ response: { status: 418 } }).message).toBe('Request failed (HTTP 418).');
  });

  it('FE-API-09 reports network failures (no response)', () => {
    expect(describeTreasuryError(new Error('Network Error'))).toEqual({
      status: null, message: 'Cannot reach the server. Check your connection and try again.', failedTransferReference: null,
    });
    expect(describeTreasuryError(undefined).status).toBeNull();
  });
});

describe('formatMoney', () => {
  it('FE-API-10 formats pesos with grouping and two decimals', () => {
    expect(formatMoney(1234.5)).toBe('₱1,234.50');
  });

  it('FE-API-11 formats zero and other currencies', () => {
    expect(formatMoney(0)).toBe('₱0.00');
    expect(formatMoney(10, 'USD')).toContain('10.00');
  });

  it('FE-API-12 keeps negative amounts readable', () => {
    expect(formatMoney(-5)).toContain('5.00');
  });
});
