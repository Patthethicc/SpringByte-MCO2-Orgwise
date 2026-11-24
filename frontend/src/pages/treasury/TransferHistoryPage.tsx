import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { describeTreasuryError, formatMoney, getMyTransfers } from '../../api/treasuryApi';
import TransferStatusBadge from './TransferStatusBadge';
import { Pager } from './TreasuryPage';

const PAGE_SIZE = 10;

/**
 * Associate "Transfer History": every disbursement paid to the signed-in officer, newest first.
 * Failed transfers are shown too, with the note that the money was returned to the treasury.
 */
export default function TransferHistoryPage() {
  const [page, setPage] = useState(0);
  const transfers = useQuery({
    queryKey: ['treasury', 'mine', page],
    queryFn: () => getMyTransfers({ page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const completedTotal = transfers.data?.content
    .filter((t) => t.status === 'COMPLETED')
    .reduce((sum, t) => sum + t.amount, 0);

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Transfer history</h1>
        <p className="text-sm text-gray-600">Money the treasury has paid out for your approved expenses.</p>
      </header>

      <section className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        {transfers.isPending && <p className="text-gray-500">Loading your transfers…</p>}
        {transfers.isError && (
          <p role="alert" className="text-red-700">
            Could not load your transfers. {describeTreasuryError(transfers.error).message}
          </p>
        )}
        {transfers.data && transfers.data.content.length === 0 && (
          <p className="text-gray-500">
            You have not received any transfers yet. Approved expenses appear here once the Finance Head disburses them.
          </p>
        )}
        {transfers.data && transfers.data.content.length > 0 && (
          <>
            <p className="mb-3 text-sm text-gray-600">
              {transfers.data.totalElements} transfer{transfers.data.totalElements === 1 ? '' : 's'} · received on this
              page: <span data-testid="page-total" className="font-semibold">{formatMoney(completedTotal ?? 0)}</span>
            </p>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
                  <tr>
                    <th className="px-3 py-2">Date</th>
                    <th className="px-3 py-2">Expense</th>
                    <th className="px-3 py-2 text-right">Amount</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Reference</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {transfers.data.content.map((t) => (
                    <tr key={t.reference}>
                      <td className="px-3 py-2 whitespace-nowrap">
                        {new Date(t.completedAt ?? t.createdAt).toLocaleDateString()}
                      </td>
                      <td className="px-3 py-2">{t.expenseTitle ?? `Expense #${t.expenseId}`}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{formatMoney(t.amount)}</td>
                      <td className="px-3 py-2">
                        <TransferStatusBadge status={t.status} />
                        {t.status === 'FAILED' && (
                          <span className="block text-xs text-gray-500">Not paid. The finance head can retry.</span>
                        )}
                      </td>
                      <td className="px-3 py-2 font-mono text-xs text-gray-500" title={t.reference}>
                        {t.reference.slice(0, 8)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={transfers.data.page} totalPages={transfers.data.totalPages} onChange={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
