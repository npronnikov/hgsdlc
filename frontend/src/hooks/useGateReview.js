import { useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import { apiRequest } from '../api/request.js';

function formatRange(range) {
  if (!range) return '';
  return `L${range.startLineNumber}:C${range.startColumn} - L${range.endLineNumber}:C${range.endColumn}`;
}

function buildReworkInstruction(requests) {
  return requests.map((request, index) => {
    const lines = [`${index + 1}.`, `Path: ${request.path}`, `Scope: ${request.scope}`, `Instruction: ${request.instruction}`];
    if (request.range) lines.push(`Range: ${formatRange(request.range)}`);
    if (request.selectedText) { lines.push('Selected fragment:', '```', request.selectedText, '```'); }
    return lines.join('\n');
  }).join('\n\n');
}

function composeReworkInstruction(instructionFromForm, requests) {
  const parts = [];
  const manual = (instructionFromForm || '').trim();
  const fromRequests = buildReworkInstruction(requests || []);
  if (manual) parts.push(manual);
  if (fromRequests.trim()) parts.push(fromRequests);
  return parts.join('\n\n');
}

function resolveDiscardUnavailableReason(reasonCode) {
  switch (reasonCode) {
    case 'rework_target_missing': return 'Rework target node is missing.';
    case 'rework_target_kind_unsupported': return 'Rework target must be an AI/Command node.';
    case 'rework_target_checkpoint_disabled': return 'Rollback before rework is unavailable because checkpoint creation is disabled on the target node.';
    case 'target_checkpoint_not_found': return 'Checkpoint is not available yet for the rework target node.';
    default: return 'Discard to checkpoint is currently unavailable.';
  }
}

export const EMPTY_REQUEST_DRAFT = {
  scope: 'file', path: '', selectedText: '', range: null, instruction: '',
};

export { formatRange, composeReworkInstruction };

export function useGateReview(gate, { onDecision, onRefresh } = {}) {
  const [approveComment, setApproveComment] = useState('');
  const [reworkComment, setReworkComment] = useState('');
  const [reworkInstruction, setReworkInstruction] = useState('');
  const [submitting, setSubmitting] = useState(null);
  const [reworkRequests, setReworkRequests] = useState([]);
  const [keepChanges, setKeepChanges] = useState(true);

  const keepChangesSelectable = gate?.payload?.rework_keep_changes_selectable === true;
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available === true;
  const reworkDiscardUnavailableReason = gate?.payload?.rework_discard_unavailable_reason || '';
  const hasAnyReworkRequests = reworkRequests.length > 0;
  const discardBlockedByRequests = reworkDiscardAvailable && hasAnyReworkRequests;
  const effectiveDiscardAvailable = reworkDiscardAvailable && !discardBlockedByRequests;
  const reworkMode = keepChanges ? 'keep' : 'discard';
  const isDiscardPolicy = reworkMode === 'discard';
  const reworkDiscardBlocked = isDiscardPolicy && !effectiveDiscardAvailable;
  const reworkDiscardBlockedReason = useMemo(() => {
    if (discardBlockedByRequests) return 'Rollback before rework is unavailable while Rework requests are present. Keep changes so the agent can apply requested edits.';
    return resolveDiscardUnavailableReason(reworkDiscardUnavailableReason);
  }, [discardBlockedByRequests, reworkDiscardUnavailableReason]);
  const hasManualReworkInstruction = !!(reworkInstruction || '').trim();
  const canSubmitRework = hasAnyReworkRequests || hasManualReworkInstruction;

  useEffect(() => {
    setKeepChanges(gate?.payload?.rework_keep_changes !== false);
  }, [gate?.gate_id, gate?.resource_version, gate?.payload?.rework_keep_changes]);

  useEffect(() => {
    if (discardBlockedByRequests && !keepChanges) {
      setKeepChanges(true);
      message.info('Discard to checkpoint was disabled because Rework requests are present.');
    }
  }, [discardBlockedByRequests, keepChanges]);

  const handleKeepChangesToggle = (checked) => {
    if (!checked && !effectiveDiscardAvailable) {
      message.warning(reworkDiscardBlockedReason);
      return;
    }
    setKeepChanges(checked);
  };

  const addReworkRequest = (draft, selectedPath) => {
    const instruction = (draft.instruction || '').trim();
    if (!instruction) { message.warning('Instruction is required'); return; }
    setReworkRequests((prev) => [...prev, {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      path: draft.path || selectedPath || '',
      scope: draft.scope || 'file',
      instruction,
      selectedText: draft.selectedText || '',
      range: draft.range || null,
      createdAt: Date.now(),
    }]);
    message.success('Rework request added');
  };

  const removeReworkRequest = (id) => {
    setReworkRequests((prev) => prev.filter((item) => item.id !== id));
  };

  const approve = async () => {
    if (!gate) return false;
    setSubmitting('approve');
    try {
      await apiRequest(`/gates/${gate.gate_id}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment: approveComment,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Gate approved');
      onDecision?.();
      return true;
    } catch (err) {
      message.error(err.message || 'Failed to approve gate');
      onRefresh?.();
      return false;
    } finally {
      setSubmitting(null);
    }
  };

  const requestRework = async () => {
    if (!gate) return false;
    if (reworkDiscardBlocked) { message.warning(reworkDiscardBlockedReason); return false; }
    if (!canSubmitRework) { message.warning('Rework instruction is required'); return false; }
    const mergedInstruction = composeReworkInstruction(reworkInstruction, reworkRequests);
    setSubmitting('rework');
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment: reworkComment,
          instruction: mergedInstruction,
          keep_changes: keepChanges,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      onDecision?.();
      return true;
    } catch (err) {
      message.error(err.message || 'Failed to request rework');
      onRefresh?.();
      return false;
    } finally {
      setSubmitting(null);
    }
  };

  return {
    approveComment, setApproveComment,
    reworkComment, setReworkComment,
    reworkInstruction, setReworkInstruction,
    submitting,
    reworkRequests, addReworkRequest, removeReworkRequest,
    keepChanges, handleKeepChangesToggle, keepChangesSelectable,
    reworkDiscardAvailable, effectiveDiscardAvailable, reworkDiscardBlocked, reworkDiscardBlockedReason,
    canSubmitRework,
    approve, requestRework,
  };
}
