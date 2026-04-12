import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Modal, Select, Space, Typography, message } from 'antd';
import { ApartmentOutlined, AppstoreOutlined, CaretRightOutlined, CloseCircleFilled } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { parse as parseYaml } from 'yaml';
import { apiRequest } from '../api/request.js';
import HumanFormViewer, { isHumanForm, validateHumanForm } from '../components/HumanFormViewer.jsx';

const { Title, Text } = Typography;
const { TextArea } = Input;

const APP_STATES = {
  IDLE: 'IDLE',
  RUN_STARTED: 'RUN_STARTED',
};

const RUN_ACTIVE_STATUSES = ['created', 'running', 'waiting_gate', 'waiting_publish', 'publish_failed'];
const HUMAN_GATE_KINDS = ['human_input', 'human_approval'];
const STAGE_ROTATING_MESSAGES = [
  'Creating great work...',
  'Understanding your system context...',
  'Mapping architecture boundaries...',
  'Reviewing existing code paths...',
  'Designing safe implementation steps...',
  'Preparing maintainable changes...',
  'Checking edge cases and failure modes...',
  'Aligning with project conventions...',
  'Keeping the quality bar high...',
  'Packaging a dev-ready result...',
];

const HEADLINE_FADE_MS = 460;
const COMPOSER_MOVE_MS = 360;
const STAGE_PAUSE_MS = 320;
const STAGE_BOTTOM_GAP_PX = 28;

function formatRunStatusLabel(status) {
  return String(status || 'unknown')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (m) => m.toUpperCase());
}

function formatDateTime(value) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date);
}

function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `pp-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function buildFlowNodeMeta(flowYaml) {
  if (!flowYaml) {
    return {
      titleById: {},
      nodeOrder: [],
    };
  }
  try {
    const parsed = parseYaml(flowYaml);
    const rawNodes = Array.isArray(parsed?.nodes) ? parsed.nodes : [];
    const titleById = {};
    const nodeOrder = [];
    for (const node of rawNodes) {
      const nodeId = node?.id;
      if (!nodeId) {
        continue;
      }
      titleById[nodeId] = node?.title || node?.name || nodeId;
      nodeOrder.push(nodeId);
    }
    return { titleById, nodeOrder };
  } catch (_) {
    return {
      titleById: {},
      nodeOrder: [],
    };
  }
}

function encodeBase64Utf8(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary);
}

function ProductOwnerInputGatePanel({ gate, onDone }) {
  const artifacts = gate?.payload?.human_input_artifacts || [];
  const [currentIndex, setCurrentIndex] = useState(0);
  const [editedByPath, setEditedByPath] = useState({});
  const [formsByPath, setFormsByPath] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const currentArtifact = artifacts[currentIndex] || null;
  const currentPath = currentArtifact?.path || '';
  const originalContent = currentArtifact?.content || '';
  const currentValue = editedByPath[currentPath] !== undefined ? editedByPath[currentPath] : originalContent;
  const parsedForm = isHumanForm(currentValue);

  const allEdited = artifacts.length === 0 || artifacts.every((a) => editedByPath[a.path] !== undefined);
  const formValidationError = (() => {
    for (const a of artifacts) {
      const val = editedByPath[a.path] !== undefined ? editedByPath[a.path] : (a.content || '');
      const f = formsByPath[a.path] || isHumanForm(val);
      if (f) {
        const err = validateHumanForm(f);
        if (err) return err;
      }
    }
    return null;
  })();

  const canSubmit = allEdited && !formValidationError;

  const handleTextChange = (value) => {
    setEditedByPath((prev) => ({ ...prev, [currentPath]: value }));
  };

  const handleFormChange = (updatedForm) => {
    const serialized = typeof updatedForm === 'string'
      ? updatedForm
      : JSON.stringify(updatedForm, null, 2);
    const parsed = isHumanForm(serialized);
    setEditedByPath((prev) => ({ ...prev, [currentPath]: serialized }));
    if (parsed) {
      setFormsByPath((prev) => ({ ...prev, [currentPath]: parsed }));
    }
  };

  const submitInput = async () => {
    if (!gate || !canSubmit) return;
    setSubmitting(true);
    try {
      const artifactPayload = artifacts.map((a) => ({
        artifact_key: a.artifact_key,
        path: a.path,
        scope: a.scope || 'run',
        content_base64: encodeBase64Utf8(editedByPath[a.path] !== undefined ? editedByPath[a.path] : (a.content || '')),
      }));
      await apiRequest(`/gates/${gate.gate_id}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          artifacts: artifactPayload,
          comment: 'submitted from product pipeline',
        }),
      });
      message.success('Input submitted');
      onDone();
    } catch (err) {
      message.error(err?.message || 'Failed to submit input');
    } finally {
      setSubmitting(false);
    }
  };

  if (artifacts.length === 0) {
    return (
      <div className="vp-inline-gate-panel">
        <Button type="primary" loading={submitting} onClick={submitInput}>Submit input</Button>
      </div>
    );
  }

  return (
    <div className="vp-inline-gate-panel">
      {gate?.payload?.user_instructions && (
        <div className="vp-inline-gate-instructions">{gate.payload.user_instructions}</div>
      )}
      {artifacts.length > 1 && (
        <div className="vp-inline-gate-steps">
          {artifacts.map((a, idx) => (
            <button
              key={a.path}
              type="button"
              className={`vp-inline-gate-step ${idx === currentIndex ? 'is-current' : ''} ${editedByPath[a.path] !== undefined ? 'is-done' : ''}`}
              onClick={() => setCurrentIndex(idx)}
            >
              {idx + 1}
            </button>
          ))}
        </div>
      )}
      <div className="vp-inline-gate-artifact-label">{currentPath}</div>
      {parsedForm ? (
        <HumanFormViewer
          formJson={parsedForm}
          onChange={handleFormChange}
        />
      ) : (
        <Input.TextArea
          value={currentValue}
          onChange={(e) => handleTextChange(e.target.value)}
          autoSize={{ minRows: 4, maxRows: 10 }}
          className="vp-inline-gate-textarea"
        />
      )}
      <Space className="vp-inline-gate-actions">
        {currentIndex > 0 && (
          <Button onClick={() => setCurrentIndex((i) => i - 1)}>Prev</Button>
        )}
        {currentIndex < artifacts.length - 1 && (
          <Button type="default" onClick={() => setCurrentIndex((i) => i + 1)}>Next</Button>
        )}
        {currentIndex === artifacts.length - 1 && (
          <Button
            type="primary"
            disabled={!canSubmit}
            loading={submitting}
            onClick={submitInput}
            title={formValidationError || (!allEdited ? 'Edit all artifacts first' : undefined)}
          >
            Submit input
          </Button>
        )}
      </Space>
    </div>
  );
}

function ProductOwnerApprovalGatePanel({ gate, onDone }) {
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const approve = async () => {
    if (!gate) return;
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Approved');
      onDone();
    } catch (err) {
      message.error(err?.message || 'Failed to approve');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="vp-inline-gate-panel">
      {gate?.payload?.user_instructions && (
        <div className="vp-inline-gate-instructions">{gate.payload.user_instructions}</div>
      )}
      <Input.TextArea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="Comment (optional)"
        autoSize={{ minRows: 2, maxRows: 4 }}
        className="vp-inline-gate-textarea"
      />
      <div className="vp-inline-gate-actions">
        <Button type="primary" loading={submitting} onClick={approve}>Approve</Button>
      </div>
    </div>
  );
}

export default function ProductPipelineMvp() {
  const [searchParams, setSearchParams] = useSearchParams();
  const shellRef = useRef(null);
  const centerZoneRef = useRef(null);
  const composerRef = useRef(null);
  const launchAttemptRef = useRef(0);
  const stopResetTimerRef = useRef(null);
  const startDockTimerRef = useRef(null);
  const startStageTimerRef = useRef(null);

  const [state, setState] = useState(APP_STATES.IDLE);
  const [task, setTask] = useState('');
  const [projects, setProjects] = useState([]);
  const [flows, setFlows] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [selectedFlow, setSelectedFlow] = useState(null);

  const [activeRunId, setActiveRunId] = useState(null);
  const [activeRun, setActiveRun] = useState(null);
  const [runNodes, setRunNodes] = useState([]);
  const [flowNodeTitlesById, setFlowNodeTitlesById] = useState({});
  const [flowNodeOrder, setFlowNodeOrder] = useState([]);
  const [progressMessageIndex, setProgressMessageIndex] = useState(0);

  const [launching, setLaunching] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [composerDocked, setComposerDocked] = useState(false);
  const [revealHeadlineOnStop, setRevealHeadlineOnStop] = useState(false);
  const [stageReady, setStageReady] = useState(false);
  const [stageGapHeight, setStageGapHeight] = useState(null);
  const runIdFromQuery = searchParams.get('runId');

  const projectOptions = useMemo(
    () => projects.map((item) => ({
      value: item.id,
      label: <span className="vp-select-option-label" title={item.name || item.id}>{item.name || item.id}</span>,
    })),
    [projects]
  );

  const flowOptions = useMemo(
    () => flows.map((item) => {
      const labelText = item.title || item.canonical_name || item.flow_id;
      return {
        value: item.flow_id,
        label: <span className="vp-select-option-label" title={labelText}>{labelText}</span>,
      };
    }),
    [flows]
  );

  const clearTimers = useCallback(() => {
    if (stopResetTimerRef.current) {
      window.clearTimeout(stopResetTimerRef.current);
      stopResetTimerRef.current = null;
    }
    if (startDockTimerRef.current) {
      window.clearTimeout(startDockTimerRef.current);
      startDockTimerRef.current = null;
    }
    if (startStageTimerRef.current) {
      window.clearTimeout(startStageTimerRef.current);
      startStageTimerRef.current = null;
    }
  }, []);

  const clearRunRuntime = useCallback(() => {
    setActiveRunId(null);
    setActiveRun(null);
    setRunNodes([]);
    setFlowNodeTitlesById({});
    setFlowNodeOrder([]);
    setProgressMessageIndex(0);
  }, []);

  useEffect(() => {
    let active = true;
    const loadProjectAndFlowOptions = async () => {
      try {
        const [projectData, flowData] = await Promise.all([
          apiRequest('/projects'),
          apiRequest('/flows'),
        ]);
        if (!active) {
          return;
        }
        const projectList = Array.isArray(projectData) ? projectData : [];
        const publishedFlows = (Array.isArray(flowData) ? flowData : [])
          .filter((item) => item?.status === 'published');

        setProjects(projectList);
        setFlows(publishedFlows);
        setSelectedProject((current) => (
          current && projectList.some((item) => item.id === current)
            ? current
            : (projectList[0]?.id || null)
        ));
        setSelectedFlow((current) => (
          current && publishedFlows.some((item) => item.flow_id === current)
            ? current
            : (publishedFlows[0]?.flow_id || null)
        ));
      } catch (err) {
        if (!active) {
          return;
        }
        setProjects([]);
        setFlows([]);
        setSelectedProject(null);
        setSelectedFlow(null);
        message.error(err?.message || 'Failed to load projects and flows');
      }
    };

    loadProjectAndFlowOptions();
    return () => {
      active = false;
    };
  }, []);

  const isRunningFlow = launching || stopping || composerDocked || state !== APP_STATES.IDLE;
  const isRunLayoutActive = composerDocked || state !== APP_STATES.IDLE || (stopping && !revealHeadlineOnStop);
  const isStageVisible = stageReady && state !== APP_STATES.IDLE;
  const isHeadlineHidden = launching || state !== APP_STATES.IDLE || (stopping && !revealHeadlineOnStop);
  const keepHeadlineSpaceOnStop = stopping && !revealHeadlineOnStop;
  const shouldRenderHeadline = !isRunLayoutActive || keepHeadlineSpaceOnStop;

  const recalcStageGapHeight = useCallback(() => {
    if (!centerZoneRef.current || !composerRef.current) {
      return;
    }
    const centerZoneRect = centerZoneRef.current.getBoundingClientRect();
    const composerRect = composerRef.current.getBoundingClientRect();
    const available = Math.floor(composerRect.top - centerZoneRect.top - STAGE_BOTTOM_GAP_PX);
    setStageGapHeight(Math.max(0, available));
  }, []);

  useEffect(() => {
    if (!isRunningFlow) {
      setStageGapHeight(null);
      return undefined;
    }

    recalcStageGapHeight();
    const rafId = window.requestAnimationFrame(recalcStageGapHeight);
    const resizeHandler = () => recalcStageGapHeight();
    window.addEventListener('resize', resizeHandler);

    const composerElement = composerRef.current;
    const transitionEndHandler = () => recalcStageGapHeight();
    composerElement?.addEventListener('transitionend', transitionEndHandler);

    return () => {
      window.cancelAnimationFrame(rafId);
      window.removeEventListener('resize', resizeHandler);
      composerElement?.removeEventListener('transitionend', transitionEndHandler);
    };
  }, [isRunningFlow, launching, stopping, state, recalcStageGapHeight]);

  useEffect(() => () => {
    clearTimers();
  }, [clearTimers]);

  useEffect(() => {
    if (stopping || !runIdFromQuery || runIdFromQuery === activeRunId) {
      return;
    }
    launchAttemptRef.current += 1;
    clearTimers();
    setStopping(false);
    setRevealHeadlineOnStop(false);
    setLaunching(false);
    setComposerDocked(true);
    setStageReady(true);
    setState(APP_STATES.RUN_STARTED);
    setActiveRunId(runIdFromQuery);
    setActiveRun(null);
    setRunNodes([]);
    setFlowNodeTitlesById({});
    setFlowNodeOrder([]);
    setProgressMessageIndex(0);
    localStorage.setItem('lastRunId', runIdFromQuery);
  }, [activeRunId, clearTimers, runIdFromQuery, stopping]);

  const loadRunRuntime = useCallback(async (runId, { silent = false } = {}) => {
    if (!runId) {
      return;
    }
    try {
      const [runResult, nodeResult] = await Promise.allSettled([
        apiRequest(`/runs/${runId}`),
        apiRequest(`/runs/${runId}/nodes`),
      ]);

      const errors = [];
      if (runResult.status === 'fulfilled') {
        setActiveRun(runResult.value || null);
      } else {
        errors.push(runResult.reason);
      }

      if (nodeResult.status === 'fulfilled') {
        setRunNodes(Array.isArray(nodeResult.value) ? nodeResult.value : []);
      } else {
        errors.push(nodeResult.reason);
      }

      if (!silent && errors.length === 2) {
        message.error(errors[0]?.message || 'Failed to load run data');
      }
    } catch (err) {
      if (!silent) {
        message.error(err?.message || 'Failed to load run data');
      }
    }
  }, []);

  useEffect(() => {
    if (!activeRunId || !isRunningFlow) {
      return undefined;
    }

    let cancelled = false;
    const poll = async () => {
      if (cancelled) {
        return;
      }
      await loadRunRuntime(activeRunId, { silent: true });
    };

    poll();

    const shouldPoll = !activeRun || RUN_ACTIVE_STATUSES.includes(activeRun.status);
    if (!shouldPoll) {
      return () => {
        cancelled = true;
      };
    }

    const timerId = window.setInterval(poll, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timerId);
    };
  }, [activeRunId, activeRun?.status, isRunningFlow, loadRunRuntime]);

  const startRun = useCallback(async () => {
    if (!task.trim()) {
      return;
    }

    const flowRecord = flows.find((item) => item.flow_id === selectedFlow);
    if (!selectedProject || !selectedFlow || !flowRecord?.canonical_name) {
      message.warning('Select project and flow before launching run');
      return;
    }

    const launchAttempt = launchAttemptRef.current + 1;
    launchAttemptRef.current = launchAttempt;

    clearTimers();
    setStopping(false);
    setComposerDocked(true);
    setRevealHeadlineOnStop(false);
    setStageReady(true);
    setState(APP_STATES.RUN_STARTED);
    clearRunRuntime();
    setLaunching(true);

    try {
      const project = projects.find((item) => item.id === selectedProject);
      const targetBranch = String(project?.default_branch || 'main').trim() || 'main';
      const [flowDetails, runResponse] = await Promise.all([
        apiRequest(`/flows/${selectedFlow}`),
        apiRequest('/runs', {
          method: 'POST',
          body: JSON.stringify({
            project_id: selectedProject,
            target_branch: targetBranch,
            flow_canonical_name: flowRecord.canonical_name,
            feature_request: task.trim(),
            ai_session_mode: 'isolated_attempt_sessions',
            publish_mode: 'branch',
            idempotency_key: generateIdempotencyKey(),
          }),
        }),
      ]);

      const runId = runResponse?.run_id;
      if (!runId) {
        throw new Error('Run id is missing in launch response');
      }
      if (launchAttempt !== launchAttemptRef.current) {
        try {
          await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
        } catch (_) {
          // ignore late cancel errors for stale launch attempts
        }
        setLaunching(false);
        return;
      }

      const flowNodeMeta = buildFlowNodeMeta(flowDetails?.flow_yaml);
      setFlowNodeTitlesById(flowNodeMeta.titleById);
      setFlowNodeOrder(flowNodeMeta.nodeOrder);
      setActiveRunId(runId);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('runId', runId);
        return next;
      }, { replace: true });
      localStorage.setItem('lastRunId', runId);
      setLaunching(false);
      await loadRunRuntime(runId);
    } catch (err) {
      clearTimers();
      clearRunRuntime();
      setLaunching(false);
      setStageReady(false);
      setComposerDocked(false);
      setStopping(false);
      setRevealHeadlineOnStop(true);
      setState(APP_STATES.IDLE);
      message.error(err?.message || 'Failed to start run');
    }
  }, [
    clearRunRuntime,
    clearTimers,
    flows,
    loadRunRuntime,
    projects,
    setSearchParams,
    selectedFlow,
    selectedProject,
    task,
  ]);

  const isFlowFinished = useMemo(
    () => ['completed', 'failed', 'publish_failed'].includes(String(activeRun?.status || '').toLowerCase()),
    [activeRun?.status]
  );

  const stopRun = useCallback(() => {
    if (stopping) {
      return;
    }

    launchAttemptRef.current += 1;
    clearTimers();

    message.success({
      key: 'product-pipeline-run-cancelled',
      content: 'Run cancelled',
      duration: 2,
    });

    if (activeRunId && activeRun && RUN_ACTIVE_STATUSES.includes(activeRun.status)) {
      apiRequest(`/runs/${activeRunId}/cancel`, { method: 'POST' }).catch(() => {
        // keep UI responsive: cancel request is best-effort and runs in background
      });
    }

    clearRunRuntime();
    setStageReady(false);
    setRevealHeadlineOnStop(false);
    setLaunching(false);
    setComposerDocked(false);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('runId');
      return next;
    }, { replace: true });
    setState(APP_STATES.IDLE);
    setStopping(true);

    stopResetTimerRef.current = window.setTimeout(() => {
      setRevealHeadlineOnStop(true);
      setStopping(false);
      stopResetTimerRef.current = null;
    }, COMPOSER_MOVE_MS);
  }, [activeRun, activeRunId, clearRunRuntime, clearTimers, setSearchParams, stopping]);

  const requestStopRun = useCallback(() => {
    if (stopping || isFlowFinished) {
      return;
    }
    Modal.confirm({
      title: 'Cancel flow?',
      content: 'Current run will be stopped. Do you want to continue?',
      okText: 'Cancel flow',
      okButtonProps: { danger: true },
      cancelText: 'Keep running',
      onOk: stopRun,
    });
  }, [isFlowFinished, stopRun, stopping]);

  const handleComposerEnter = (event) => {
    if (event.shiftKey) {
      return;
    }
    event.preventDefault();
    if (!isRunningFlow && task.trim() && selectedProject && selectedFlow && !launching) {
      startRun();
    }
  };

  const currentNodeId = useMemo(() => {
    if (activeRun?.current_node_id) {
      return activeRun.current_node_id;
    }
    const runningNode = runNodes.find((item) => item.status === 'running');
    if (runningNode?.node_id) {
      return runningNode.node_id;
    }
    return runNodes[runNodes.length - 1]?.node_id || null;
  }, [activeRun?.current_node_id, runNodes]);

  const currentNodeTitle = useMemo(() => {
    if (!currentNodeId) {
      return null;
    }
    const direct = flowNodeTitlesById[currentNodeId];
    if (direct) {
      return direct;
    }
    const currentExecution = runNodes.find((item) => item.node_id === currentNodeId);
    return currentExecution?.node_title || currentExecution?.title || currentNodeId;
  }, [currentNodeId, flowNodeTitlesById, runNodes]);

  const runningStepInfo = useMemo(() => {
    const uniqueExecutedNodes = Array.from(new Set(runNodes.map((node) => node?.node_id).filter(Boolean)));
    const totalSteps = Math.max(
      flowNodeOrder.length,
      uniqueExecutedNodes.length,
      1
    );

    if (!currentNodeId) {
      return { step: 1, total: totalSteps };
    }

    const flowIndex = flowNodeOrder.indexOf(currentNodeId);
    if (flowIndex >= 0) {
      return { step: flowIndex + 1, total: totalSteps };
    }

    const executedIndex = uniqueExecutedNodes.indexOf(currentNodeId);
    if (executedIndex >= 0) {
      return { step: executedIndex + 1, total: totalSteps };
    }

    return { step: 1, total: totalSteps };
  }, [currentNodeId, flowNodeOrder, runNodes]);

  const statusText = useMemo(() => {
    if (!activeRun) {
      return 'Waiting for run status…';
    }
    if (activeRun.status === 'running') {
      return `Running step ${runningStepInfo.step} of ${runningStepInfo.total}`;
    }
    return `${formatRunStatusLabel(activeRun.status)}${activeRun?.publish_status ? ` · publish ${formatRunStatusLabel(activeRun.publish_status)}` : ''}`;
  }, [activeRun, runningStepInfo.step, runningStepInfo.total]);

  const currentGate = activeRun?.current_gate || null;
  const hasHumanGate = currentGate && HUMAN_GATE_KINDS.includes(currentGate.gate_kind);
  const hasHumanInputGate = hasHumanGate && currentGate.gate_kind === 'human_input';
  const isCancelDisabled = isFlowFinished || stopping;
  const rotatingProgressMessage = isFlowFinished
    ? 'Flow completed, you can watch results now'
    : STAGE_ROTATING_MESSAGES[progressMessageIndex];

  useEffect(() => {
    if (!activeRunId || !isStageVisible) {
      setProgressMessageIndex(0);
      return undefined;
    }
    if (hasHumanGate || isFlowFinished) {
      return undefined;
    }
    const timerId = window.setInterval(() => {
      setProgressMessageIndex((value) => ((value + 1) % STAGE_ROTATING_MESSAGES.length));
    }, 3200);
    return () => window.clearInterval(timerId);
  }, [activeRunId, hasHumanGate, isFlowFinished, isStageVisible]);

  const renderStageSkeleton = (title, dynamicText) => (
    <div className="vp-center-panel vp-center-panel-skeleton">
      <Text className="vp-stage-kicker">Pipeline status</Text>
      <Title level={3} className="vp-stage-title">{title}</Title>
      <div className="vp-skeleton-stack" aria-hidden="true">
        <span className="vp-skeleton-line vp-skeleton-line-lg" />
        <span className="vp-skeleton-line" />
        <span className="vp-skeleton-line vp-skeleton-line-sm" />
      </div>
      <Text key={dynamicText} className="vp-stage-live-text">{dynamicText}</Text>
    </div>
  );

  const renderLiveRunPanel = () => {
    if (!activeRunId) {
      return renderStageSkeleton('Spinning up delivery pipeline', 'Submitting run request…');
    }

    return (
      <div className={`vp-center-panel vp-live-run-panel ${hasHumanInputGate ? 'is-human-input' : ''}`}>
        <Text className="vp-stage-kicker">Pipeline status</Text>
        <Title level={3} className="vp-stage-title">{currentNodeTitle || 'Spinning up delivery pipeline'}</Title>
        <Text className="vp-stage-live-text">{statusText}</Text>
        <Text className="vp-live-run-meta mono">Run {activeRunId.slice(0, 12)} · started {formatDateTime(activeRun?.created_at)}</Text>

        {!hasHumanGate && (
          <div className="vp-live-message-strip">
            <Text key={isFlowFinished ? 'flow-finished' : progressMessageIndex} className="vp-live-message-text">
              {rotatingProgressMessage}
            </Text>
          </div>
        )}

        {hasHumanGate && currentGate.gate_kind === 'human_input' && (
          <ProductOwnerInputGatePanel
            gate={currentGate}
            onDone={() => loadRunRuntime(activeRunId)}
          />
        )}
        {hasHumanGate && currentGate.gate_kind === 'human_approval' && (
          <ProductOwnerApprovalGatePanel
            gate={currentGate}
            onDone={() => loadRunRuntime(activeRunId)}
          />
        )}
      </div>
    );
  };

  const renderCenterContent = () => {
    if (!isStageVisible) {
      return null;
    }
    return renderLiveRunPanel();
  };

  return (
    <div className={`vp-pipeline-shell ${isRunLayoutActive ? 'is-running' : ''}`} ref={shellRef}>
      <div className="vp-center-zone" ref={centerZoneRef}>
        {shouldRenderHeadline && (
          <div className={`vp-entry-headline ${isHeadlineHidden ? 'is-hidden' : ''} ${keepHeadlineSpaceOnStop ? 'is-return-prepared' : ''}`}>
            <Title level={2} className="vp-entry-title">What are we inventing today?</Title>
            <Text className="vp-entry-subtitle">
              Describe the business request and the system will drive it to a dev-ready result.
            </Text>
          </div>
        )}

        <div
          className={`vp-stage-center ${isStageVisible ? 'is-visible' : ''} ${hasHumanInputGate ? 'is-human-input-gate' : ''}`}
          style={isRunningFlow && stageGapHeight !== null ? { '--vp-stage-gap-height': `${stageGapHeight}px` } : undefined}
        >
          {renderCenterContent()}
        </div>

        <div className={`vp-composer-zone ${stopping ? 'is-returning' : (composerDocked ? 'is-docked' : 'is-idle')}`} ref={composerRef}>
          <div className={`vp-composer-shell ${isRunningFlow ? 'is-locked is-cancel-only' : ''}`}>
            {isRunningFlow ? (
              <div className={`vp-cancel-dock ${stopping ? 'is-returning' : 'is-running'} ${isCancelDisabled ? 'is-disabled' : ''}`}>
                <span className="vp-cancel-ring vp-cancel-ring-1" />
                <span className="vp-cancel-ring vp-cancel-ring-2" />
                <span className="vp-cancel-ring vp-cancel-ring-3" />
                <Button
                  type="primary"
                  size="large"
                  danger
                  icon={<CloseCircleFilled />}
                  className="vp-cancel-orb-btn"
                  onClick={requestStopRun}
                  disabled={isCancelDisabled}
                >
                  Cancel
                </Button>
              </div>
            ) : (
              <>
                <TextArea
                  value={task}
                  onChange={(event) => setTask(event.target.value)}
                  onPressEnter={handleComposerEnter}
                  placeholder="Ask anything"
                  autoSize={{ minRows: 2, maxRows: 2 }}
                  className="vp-entry-input"
                />

                <div className="vp-composer-controls">
                  <div className="vp-tool-row">
                    <div className="vp-select-shell vp-select-shell-project">
                      <AppstoreOutlined />
                      <Select
                        value={selectedProject || undefined}
                        options={projectOptions}
                        onChange={setSelectedProject}
                        placeholder="Select project"
                        style={{ width: '100%' }}
                        className="vp-inline-select"
                        popupClassName="vp-select-dropdown"
                        optionLabelProp="label"
                        showSearch={false}
                        variant="borderless"
                      />
                    </div>
                    <div className="vp-select-shell vp-select-shell-flow">
                      <ApartmentOutlined />
                      <Select
                        value={selectedFlow || undefined}
                        options={flowOptions}
                        onChange={setSelectedFlow}
                        placeholder="Select flow"
                        style={{ width: '100%' }}
                        className="vp-inline-select"
                        popupClassName="vp-select-dropdown"
                        optionLabelProp="label"
                        showSearch={false}
                        variant="borderless"
                      />
                    </div>
                  </div>

                  <Button
                    type="primary"
                    shape="circle"
                    icon={<CaretRightOutlined />}
                    className="vp-run-icon-btn is-run"
                    onClick={startRun}
                    disabled={!task.trim() || !selectedProject || !selectedFlow || launching}
                    aria-label="Run"
                  />
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
