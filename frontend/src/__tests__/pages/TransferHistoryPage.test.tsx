import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TransferHistoryPage from '../../pages/treasury/TransferHistoryPage';
import * as api from '../../api/treasuryApi';
import type { Page, TransferRecord } from '../../api/treasuryApi';

vi.mock('../../api/treasuryApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/treasuryApi')>();
  return { ...actual, getMyTransfers: vi.fn() };
});

const mocked = vi.mocked(api);

// Explicit cleanup so the tests do not depend on Vitest `globals` being enabled.
afterEach(cleanup);

function transfer(overrides: Partial<TransferRecord> = {}): TransferRecord {
  return {
    id: 1, reference: 'abcdef12-3456-7890-abcd-ef1234567890', expenseId: 5, eventId: 3, expenseTitle: 'Venue deposit',
    amount: 1500, recipientId: 7, recipientName: 'Ana Reyes', disbursedById: 2, disbursedByUsername: 'finance',
    status: 'COMPLETED', failureReason: null, revertPending: false, createdAt: '2026-11-20T08:00:00Z',
    completedAt: '2026-11-20T08:00:01Z', ...overrides,
  };
}

function page(content: TransferRecord[], extra: Partial<Page<TransferRecord>> = {}): Page<TransferRecord> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TransferHistoryPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.resetAllMocks());

describe('TransferHistoryPage', () => {
  it('FE-TH-01 lists my transfers with amount, status and a short reference', async () => {
    mocked.getMyTransfers.mockResolvedValue(page([transfer()]));
    renderPage();
    expect(await screen.findByText('Venue deposit')).toBeInTheDocument();
    expect(screen.getAllByText('₱1,500.00').length).toBeGreaterThan(0);
    expect(screen.getByText('Completed')).toBeInTheDocument();
    expect(screen.getByText('abcdef12')).toHaveAttribute('title', 'abcdef12-3456-7890-abcd-ef1234567890');
  });

  it('FE-TH-02 totals only COMPLETED transfers on the page', async () => {
    mocked.getMyTransfers.mockResolvedValue(page([
      transfer({ reference: 'a', amount: 1000 }),
      transfer({ reference: 'b', amount: 250.5 }),
      transfer({ reference: 'c', amount: 9999, status: 'FAILED' }),
    ]));
    renderPage();
    expect(await screen.findByTestId('page-total')).toHaveTextContent('₱1,250.50');
    expect(screen.getByText('3 transfers · received on this page:', { exact: false })).toBeInTheDocument();
  });

  it('FE-TH-03 explains FAILED transfers were not paid', async () => {
    mocked.getMyTransfers.mockResolvedValue(page([transfer({ status: 'FAILED', completedAt: null })]));
    renderPage();
    expect(await screen.findByText('Failed (refunded)')).toBeInTheDocument();
    expect(screen.getByText('Not paid. The finance head can retry.')).toBeInTheDocument();
  });

  it('FE-TH-04 shows an empty state', async () => {
    mocked.getMyTransfers.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText(/You have not received any transfers yet/)).toBeInTheDocument();
  });

  it('FE-TH-05 shows a loading state, then an error with the 503 message', async () => {
    mocked.getMyTransfers.mockRejectedValue(Object.assign(new Error('x'), { response: { status: 503, data: {} } }));
    renderPage();
    expect(screen.getByText('Loading your transfers…')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(api.SERVICE_UNAVAILABLE_MESSAGE);
  });

  it('FE-TH-06 pages forward and back', async () => {
    const user = userEvent.setup();
    mocked.getMyTransfers.mockImplementation(async ({ page: p = 0 } = {}) =>
      page([transfer({ reference: `r${p}`, expenseTitle: `Expense ${p}` })], { page: p, totalPages: 2, totalElements: 2 }));
    renderPage();
    expect(await screen.findByText('Expense 0')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Expense 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    expect(await screen.findByText('Expense 0')).toBeInTheDocument();
    expect(mocked.getMyTransfers).toHaveBeenCalledWith({ page: 1, size: 10 });
  });

  it('FE-TH-07 uses singular wording for one transfer', async () => {
    mocked.getMyTransfers.mockResolvedValue(page([transfer()]));
    renderPage();
    expect(await screen.findByText('1 transfer · received on this page:', { exact: false })).toBeInTheDocument();
  });
});
