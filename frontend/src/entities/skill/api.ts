export interface Skill {
  id: string;
  name: string;
  version: string;
  description: string;
  handler: string;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  status: SkillStatus;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export type SkillStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'DEPRECATED'
  | 'ARCHIVED';
