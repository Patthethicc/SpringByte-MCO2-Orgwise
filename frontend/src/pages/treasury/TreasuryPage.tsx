import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  describeTreasuryError,
  disburseExpense,
  formatMoney,
  getDisbursableExpenses,
  getTransfers,
  getTreasury,
  type DisbursableExpense,
  type TransferRecord,
  type TreasuryErrorInfo,
} from '../../api/treasuryApi';
import TransferStatusBadge from './TransferStatusBadge';

/** React Query keys used by the treasury pages. */
export const treasuryKeys = {
  account: ['treasury', 'account'] as const,
  disbursable: ['treasury', 'disbursable'] as const,
  ledger: (page: number) => ['treasury', 'ledger', page] as const,
};

const LEDGER_PAGE_SIZE = 10;

type Feedback =
  | { kind: 'success'; transfer: TransferRecord }
  | { kind: 'error'; info: TreasuryErrorInfo };

/**
 * Finance Head treasury page: current balance, approved expenses with a "Disburse" action
 * (runs the disbursement saga), and the recent ledger. Shows clear messages for 503
 * (service down, no money moved), 409/422 (rule violations) and compensated sagas.
 */
export default function TreasuryPage() {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState<DisbursableExpense | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [ledgerPage, setLedgerPage] = useState(0);

  const account = useQuery({ queryKey: treasuryKeys.account, queryFn: getTreasury });
  const disbursable = useQuery({ queryKey: treasuryKeys.disbursable, queryFn: getDisbursableExpenses });
  const ledger = useQuery({
    queryKey: treasuryKeys.ledger(ledgerPage),
    queryFn: () => getTransfers({ page: ledgerPage, size: LEDGER_PAGE_SIZE }),
  });

  const disburse = useMutation({
    mutationFn: (expense: DisbursableExpense) => disburseExpense(expense.id),
    onSuccess: (transfer) => setFeedback({ kind: 'success', transfer }),
    onError: (error) => setFeedback({ kind: 'error', info: describeTreasuryError(error) }),
    onSettled: () => {
      setConfirming(null);
      // Refresh even on failure: a compensated saga leaves a FAILED row in the ledger.
      void queryClient.invalidateQueries({ queryKey: ['treasury'] });
    },
  });

  const currency = account.data?.currency ?? 'PHP';

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Treasury</h1>
        <p className="text-sm text-gray-600">Disburse approved expenses and review the transfer ledger.</p>
      </header>

      <section aria-labelledby="balance-heading" className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <h2 id="balance-heading" className="text-sm font-medium uppercase tracking-wide text-gray-500">
          Current balance
        </h2>
        {account.isPending && <p className="mt-2 text-gray-500">Loading balance…</p>}
        {account.isError && (
          <p role="alert" className="mt-2 text-red-700">
            Could not load the treasury balance. {describeTreasuryError(account.error).message}
          </p>
        )}
        {account.data && (
          <>
            <p data-testid="treasury-balance" className="mt-1 text-3xl font-semibold text-gray-900">
              {formatMoney(account.data.balance, currency)}
            </p>
            <p className="text-xs text-gray-500">{account.data.name}</p>
          </>
        )}
      </section>

      {feedback?.kind === 'success' && (
        <div role="status" className="rounded-md border border-green-300 bg-green-50 p-4 text-green-800">
          Disbursed {formatMoney(feedback.transfer.amount, currency)} to{' '}
          {feedback.transfer.recipientName ?? 'the requester'}. Reference{' '}
          <span className="font-mono">{feedback.transfer.reference}</span>.
        </div>
      )}
      {feedback?.kind === 'error' && (
        <div role="alert" className="rounded-md border border-red-300 bg-red-50 p-4 text-red-800">
          <p className="font-semibold">
            {feedback.info.status === 503 ? 'Service unavailable, nothing was paid' : 'Disbursement failed'}
          </p>
          <p>{feedback.info.message}</p>
          {feedback.info.failedTransferReference && (
            <p className="mt-1 text-sm">
              Transfer <span className="font-mono">{feedback.info.failedTransferReference}</span> was marked FAILED and
              the balance was restored.
            </p>
          )}
        </div>
      )}

      <section aria-labelledby="approved-heading" className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <h2 id="approved-heading" className="mb-3 text-lg font-semibold text-gray-900">
          Approved expenses awaiting disbursement
        </h2>
        {disbursable.isPending && <p className="text-gray-500">Loading approved expenses…</p>}
        {disbursable.isError && (
          <div role="alert" className="rounded-md border border-amber-300 bg-amber-50 p-3 text-amber-900">
            {describeTreasuryError(disbursable.error).status === 503
              ? 'The expense service is unavailable, so approved expenses cannot be listed right now. No disbursements can be made until it is back.'
              : describeTreasuryError(disbursable.error).message}
          </div>
        )}
        {disbursable.data && disbursable.data.length === 0 && (
          <p className="text-gray-500">No approved expenses are waiting to be paid.</p>
        )}
        {disbursable.data && disbursable.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-3 py-2">Expense</th>
                  <th className="px-3 py-2">Requester</th>
                  <th className="px-3 py-2 text-right">Amount</th>
                  <th className="px-3 py-2" aria-label="Actions" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {disbursable.data.map((expense) => {
                  const insufficient = account.data ? expense.amount > account.data.balance : false;
                  return (
                    <tr key={expense.id}>
                      <td className="px-3 py-2">
                        <span className="font-medium">{expense.title ?? `Expense #${expense.id}`}</span>
                        <span className="block text-xs text-gray-500">#{expense.id}</span>
                      </td>
                      <td className="px-3 py-2">{expense.requesterName ?? '—'}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{formatMoney(expense.amount, currency)}</td>
                      <td className="px-3 py-2 text-right">
                        <button
                          type="button"
                          className="rounded bg-indigo-600 px-3 py-1 text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
                          disabled={disburse.isPending || insufficient}
                          title={insufficient ? 'Treasury balance is too low' : undefined}
                          onClick={() => {
                            setFeedback(null);
                            setConfirming(expense);
                          }}
                        >
                          Disburse
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {confirming && (
        <div role="dialog" aria-modal="true" aria-labelledby="confirm-heading"
             className="rounded-lg border border-indigo-300 bg-indigo-50 p-4">
          <h3 id="confirm-heading" className="font-semibold text-indigo-900">Confirm disbursement</h3>
          <p className="text-indigo-900">
            Pay {formatMoney(confirming.amount, currency)} to {confirming.requesterName ?? 'the requester'} for “
            {confirming.title ?? `Expense #${confirming.id}`}”?
          </p>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              className="rounded bg-indigo-600 px-3 py-1 text-white disabled:bg-gray-300"
              disabled={disburse.isPending}
              onClick={() => disburse.mutate(confirming)}
            >
              {disburse.isPending ? 'Disbursing…' : 'Confirm'}
            </button>
            <button
              type="button"
              className="rounded border border-gray-300 bg-white px-3 py-1"
              disabled={disburse.isPending}
              onClick={() => setConfirming(null)}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <section aria-labelledby="ledger-heading" className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <h2 id="ledger-heading" className="mb-3 text-lg font-semibold text-gray-900">Recent transfers</h2>
        {ledger.isPending && <p className="text-gray-500">Loading transfers…</p>}
        {ledger.isError && (
          <p role="alert" className="text-red-700">{describeTreasuryError(ledger.error).message}</p>
        )}
        {ledger.data && ledger.data.content.length === 0 && <p className="text-gray-500">No transfers yet.</p>}
        {ledger.data && ledger.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
                  <tr>
                    <th className="px-3 py-2">Date</th>
                    <th className="px-3 py-2">Expense</th>
                    <th className="px-3 py-2">Recipient</th>
                    <th className="px-3 py-2 text-right">Amount</th>
                    <th className="px-3 py-2">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {ledger.data.content.map((t) => (
                    <tr key={t.reference}>
                      <td className="px-3 py-2 whitespace-nowrap">{new Date(t.createdAt).toLocaleString()}</td>
                      <td className="px-3 py-2">{t.expenseTitle ?? `Expense #${t.expenseId}`}</td>
                      <td className="px-3 py-2">{t.recipientName ?? '—'}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{formatMoney(t.amount, currency)}</td>
                      <td className="px-3 py-2">
                        <TransferStatusBadge status={t.status} />
                        {t.status === 'FAILED' && t.failureReason && (
                          <span className="block text-xs text-gray-500">{t.failureReason}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={ledger.data.page} totalPages={ledger.data.totalPages} onChange={setLedgerPage} />
          </>
        )}
      </section>
    </div>
  );
}

/**
 * Previous / next controls for a paged list.
 * @param props.page zero-based current page
 * @param props.totalPages number of pages
 * @param props.onChange called with the new page
 */
export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav aria-label="Pagination" className="mt-3 flex items-center justify-between text-sm">
      <button type="button" className="rounded border px-3 py-1 disabled:opacity-40" disabled={page <= 0}
              onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>Page {page + 1} of {totalPages}</span>
      <button type="button" className="rounded border px-3 py-1 disabled:opacity-40" disabled={page >= totalPages - 1}
              onClick={() => onChange(page + 1)}>
        Next
      </button>
    </nav>
  );
}
