export interface Gate {
  id: string;
  runId: string;
  stageId: string;
  gateType: GateType;
  status: GateStatus;
  requiredApprovers: number;
  approvedBy: string[];
  rejectedBy: string[];
  createdAt: string;
  resolvedAt: string | null;
}

export type GateType =
  | 'MANUAL_APPROVAL'
  | 'AUTOMATIC'
  | 'POLICY_CHECK'
  | 'EXTERNAL_SIGNAL';

export type GateStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED';
