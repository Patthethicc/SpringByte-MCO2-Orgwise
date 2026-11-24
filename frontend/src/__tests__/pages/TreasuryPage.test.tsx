import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TreasuryPage, { Pager } from '../../pages/treasury/TreasuryPage';
import * as api from '../../api/treasuryApi';
import type { DisbursableExpense, Page, TransferRecord } from '../../api/treasuryApi';

vi.mock('../../api/treasuryApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/treasuryApi')>();
  return {
    ...actual,
    getTreasury: vi.fn(),
    getDisbursableExpenses: vi.fn(),
    getTransfers: vi.fn(),
    disburseExpense: vi.fn(),
  };
});

const mocked = vi.mocked(api);

// Explicit cleanup so the tests do not depend on Vitest `globals` being enabled.
afterEach(cleanup);

const expense: DisbursableExpense = {
  id: 5, title: 'Venue deposit', amount: 2500, status: 'APPROVED', requesterId: 7, requesterName: 'Ana Reyes', eventId: 3,
};

function transfer(overrides: Partial<TransferRecord> = {}): TransferRecord {
  return {
    id: 1, reference: 'a1b2c3d4-0000-0000-0000-000000000001', expenseId: 5, eventId: 3, expenseTitle: 'Venue deposit',
    amount: 2500, recipientId: 7, recipientName: 'Ana Reyes', disbursedById: 2, disbursedByUsername: 'finance',
    status: 'COMPLETED', failureReason: null, revertPending: false, createdAt: '2026-11-24T08:00:00Z',
    completedAt: '2026-11-24T08:00:01Z', ...overrides,
  };
}

function page<T>(content: T[], extra: Partial<Page<T>> = {}): Page<T> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra };
}

function httpError(status: number, data?: unknown) {
  return Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, response: { status, data } });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TreasuryPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  mocked.getTreasury.mockResolvedValue({ id: 1, name: 'Main Treasury', balance: 100000, currency: 'PHP', updatedAt: '' });
  mocked.getDisbursableExpenses.mockResolvedValue([expense]);
  mocked.getTransfers.mockResolvedValue(page([transfer()]));
});

describe('TreasuryPage: balance', () => {
  it('FE-TR-01 shows the formatted treasury balance', async () => {
    renderPage();
    expect(await screen.findByTestId('treasury-balance')).toHaveTextContent('₱100,000.00');
    expect(screen.getByText('Main Treasury')).toBeInTheDocument();
  });

  it('FE-TR-02 shows a loading state before the balance arrives', () => {
    mocked.getTreasury.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText('Loading balance…')).toBeInTheDocument();
  });

  it('FE-TR-03 shows an error when the balance cannot be loaded', async () => {
    mocked.getTreasury.mockRejectedValue(httpError(500, { status: 500, message: 'An unexpected error occurred' }));
    renderPage();
    expect(await screen.findByText(/Could not load the treasury balance/)).toHaveTextContent('An unexpected error occurred');
  });
});

describe('TreasuryPage: approved expenses', () => {
  it('FE-TR-04 lists approved expenses with requester and amount', async () => {
    renderPage();
    const section = (await screen.findByRole('heading', { name: /approved expenses/i })).closest('section')!;
    expect(await within(section).findByText('Venue deposit')).toBeInTheDocument();
    expect(within(section).getByText('Ana Reyes')).toBeInTheDocument();
    expect(within(section).getByText('₱2,500.00')).toBeInTheDocument();
  });

  it('FE-TR-05 shows an empty state when nothing is waiting', async () => {
    mocked.getDisbursableExpenses.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No approved expenses are waiting to be paid.')).toBeInTheDocument();
  });

  it('FE-TR-06 explains a 503 from the expense service instead of showing a table', async () => {
    mocked.getDisbursableExpenses.mockRejectedValue(httpError(503, { status: 503, message: 'Expense service is unavailable.' }));
    renderPage();
    expect(await screen.findByText(/expense service is unavailable, so approved expenses cannot be listed/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disburse' })).not.toBeInTheDocument();
  });

  it('FE-TR-07 disables Disburse when the balance is lower than the amount', async () => {
    mocked.getTreasury.mockResolvedValue({ id: 1, name: 'Main', balance: 100, currency: 'PHP', updatedAt: '' });
    renderPage();
    await screen.findByText('₱100.00');
    expect(await screen.findByRole('button', { name: 'Disburse' })).toBeDisabled();
  });
});

describe('TreasuryPage: disburse flow', () => {
  it('FE-TR-08 asks for confirmation, disburses, shows success and refreshes the data', async () => {
    const user = userEvent.setup();
    mocked.disburseExpense.mockResolvedValue(transfer());
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Disburse' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Pay ₱2,500.00 to Ana Reyes');
    await user.click(within(dialog).getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Disbursed ₱2,500.00 to Ana Reyes');
    expect(mocked.disburseExpense).toHaveBeenCalledWith(5);
    await waitFor(() => expect(mocked.getTreasury).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('FE-TR-09 cancel closes the confirmation without calling the API', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Disburse' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mocked.disburseExpense).not.toHaveBeenCalled();
  });

  it('FE-TR-10 a 503 says nothing was paid and names the FAILED (refunded) transfer', async () => {
    const user = userEvent.setup();
    mocked.disburseExpense.mockRejectedValue(httpError(503, {
      status: 503,
      message: 'Expense service could not confirm the disbursement. The balance was restored and transfer ref-9 is FAILED.',
      details: { transferReference: 'ref-9' },
    }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Disburse' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Service unavailable, nothing was paid');
    expect(alert).toHaveTextContent('Transfer ref-9 was marked FAILED and the balance was restored.');
  });

  it('FE-TR-11 a 409 rule violation shows the server message', async () => {
    const user = userEvent.setup();
    mocked.disburseExpense.mockRejectedValue(httpError(409, { status: 409, message: 'Expense 5 has already been disbursed or is being disbursed' }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Disburse' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Disbursement failed');
    expect(alert).toHaveTextContent('already been disbursed');
    expect(alert).not.toHaveTextContent('marked FAILED');
  });

  it('FE-TR-12 the confirm button shows progress and cannot be double-clicked', async () => {
    const user = userEvent.setup();
    let resolve!: (t: TransferRecord) => void;
    mocked.disburseExpense.mockReturnValue(new Promise((r) => { resolve = r; }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Disburse' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByRole('button', { name: 'Disbursing…' })).toBeDisabled();
    expect(mocked.disburseExpense).toHaveBeenCalledTimes(1);
    resolve(transfer());
    expect(await screen.findByRole('status')).toBeInTheDocument();
  });
});

describe('TreasuryPage: ledger', () => {
  it('FE-TR-13 lists recent transfers with status badges and failure reasons', async () => {
    mocked.getTransfers.mockResolvedValue(page([
      transfer(),
      transfer({ reference: 'r2', status: 'FAILED', failureReason: 'Expense service did not confirm', expenseTitle: 'Snacks' }),
    ]));
    renderPage();
    const ledger = (await screen.findByRole('heading', { name: 'Recent transfers' })).closest('section')!;
    expect(await within(ledger).findByText('Completed')).toBeInTheDocument();
    expect(within(ledger).getByText('Failed (refunded)')).toBeInTheDocument();
    expect(within(ledger).getByText('Expense service did not confirm')).toBeInTheDocument();
  });

  it('FE-TR-14 shows an empty ledger message', async () => {
    mocked.getTransfers.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('No transfers yet.')).toBeInTheDocument();
  });

  it('FE-TR-15 pages through the ledger', async () => {
    const user = userEvent.setup();
    mocked.getTransfers.mockImplementation(async ({ page: p = 0 } = {}) =>
      page([transfer({ reference: `r${p}`, expenseTitle: `Item ${p}` })], { page: p, totalPages: 3, totalElements: 3 }));
    renderPage();
    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Item 1')).toBeInTheDocument();
    expect(mocked.getTransfers).toHaveBeenLastCalledWith({ page: 1, size: 10 });
  });
});

describe('Pager', () => {
  it('FE-PG-01 renders nothing for a single page', () => {
    const { container } = render(<Pager page={0} totalPages={1} onChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('FE-PG-02 disables Previous on the first page and Next on the last', () => {
    const { rerender } = render(<Pager page={0} totalPages={2} onChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    rerender(<Pager page={1} totalPages={2} onChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('FE-PG-03 calls onChange with the neighbouring page', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Pager page={1} totalPages={3} onChange={onChange} />);
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(onChange.mock.calls).toEqual([[0], [2]]);
  });
});
