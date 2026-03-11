export interface Run {
  id: string;
  flowId: string;
  flowVersion: string;
  status: RunStatus;
  currentStage: string | null;
  context: Record<string, unknown>;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  createdBy: string;
}

export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'WAITING_FOR_GATE'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface RunStage {
  id: string;
  runId: string;
  stageId: string;
  status: StageStatus;
  startedAt: string | null;
  completedAt: string | null;
}

export type StageStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED';
