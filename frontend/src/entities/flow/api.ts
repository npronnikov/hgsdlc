export interface Flow {
  id: string;
  name: string;
  version: string;
  description: string;
  status: FlowStatus;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export type FlowStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'DEPRECATED'
  | 'ARCHIVED';
