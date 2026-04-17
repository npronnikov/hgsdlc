import { useMemo, useState } from 'react';
import { message } from 'antd';
import { isHumanForm, validateHumanForm } from '../components/HumanFormViewer.jsx';
import { apiRequest } from '../api/request.js';

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary);
}

export { encodeBase64, isHumanForm };

export function useGateInput(gate, { onSubmitted } = {}) {
  const [editedByPath, setEditedByPath] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const editableArtifacts = useMemo(
    () => (Array.isArray(gate?.payload?.human_input_artifacts) ? gate.payload.human_input_artifacts : []),
    [gate?.payload?.human_input_artifacts],
  );

  const editablePaths = useMemo(
    () => editableArtifacts.map((a) => a?.workspace_path || a?.path || '').filter(Boolean),
    [editableArtifacts],
  );

  const updateArtifact = (path, content) => {
    setEditedByPath((prev) => ({ ...prev, [path]: content }));
  };

  const submitInput = async () => {
    if (!gate) return;
    const changedArtifacts = editableArtifacts
      .map((artifact) => ({ artifact, content: editedByPath[artifact.path] }))
      .filter(({ content, artifact }) => content !== undefined && content !== (artifact.content || ''));

    if (changedArtifacts.length === 0) {
      message.warning('No editable artifacts changed');
      return;
    }
    for (const { content } of changedArtifacts) {
      const formError = validateHumanForm(isHumanForm(content));
      if (formError) { message.warning(formError); return; }
    }

    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          artifacts: changedArtifacts.map(({ artifact, content }) => ({
            artifact_key: artifact.artifact_key,
            path: artifact.path,
            scope: artifact.scope || 'run',
            content_base64: encodeBase64(content),
          })),
          comment: 'submitted from gate panel',
        }),
      });
      message.success('Input submitted');
      onSubmitted?.();
    } catch (err) {
      message.error(err.message || 'Failed to submit input');
    } finally {
      setSubmitting(false);
    }
  };

  return {
    editableArtifacts, editablePaths,
    editedByPath, updateArtifact,
    submitting, submitInput,
  };
}
