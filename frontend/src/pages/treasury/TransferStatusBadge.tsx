import type { TransferStatus } from '../../api/treasuryApi';

const STYLES: Record<TransferStatus, string> = {
  COMPLETED: 'bg-green-100 text-green-800',
  PENDING: 'bg-amber-100 text-amber-800',
  FAILED: 'bg-red-100 text-red-800',
};

const LABELS: Record<TransferStatus, string> = {
  COMPLETED: 'Completed',
  PENDING: 'Processing',
  FAILED: 'Failed (refunded)',
};

/**
 * Coloured pill for a transfer's saga status.
 * @param props.status transfer status
 */
export default function TransferStatusBadge({ status }: { status: TransferStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${STYLES[status]}`}>
      {LABELS[status]}
    </span>
  );
}
