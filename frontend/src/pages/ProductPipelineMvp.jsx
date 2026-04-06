import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Checkbox, Input, Radio, Select, Typography } from 'antd';
import { AppstoreOutlined, CaretRightOutlined, CloseCircleFilled } from '@ant-design/icons';

const { Title, Text } = Typography;
const { TextArea } = Input;

const APP_STATES = {
  IDLE: 'IDLE',
  RUN_STARTED: 'RUN_STARTED',
  CLARIFICATION: 'CLARIFICATION',
  CONFIRMATION: 'CONFIRMATION',
  PLANNING: 'PLANNING',
  IMPLEMENTATION: 'IMPLEMENTATION',
  REVIEW: 'REVIEW',
  DEPLOY: 'DEPLOY',
  DONE: 'DONE',
};

const MOCK_PROJECTS = [
  { id: 'payments-api-v2', name: 'Payments API v2' },
  { id: 'reporting-core', name: 'Reporting Core' },
];

const CLARIFICATION_QUESTIONS = {
  audience: {
    title: 'Who will use this feature?',
    options: ['Only administrators', 'All users', 'External clients'],
    required: true,
  },
  formats: {
    title: 'Which export formats are required?',
    options: ['PDF', 'CSV', 'Excel'],
    required: true,
  },
  constraints: {
    title: 'Any constraints or special requirements?',
    required: false,
  },
};

const RUN_BOOT_PHASES = [
  'Request accepted',
  'Project context loaded',
  'Preparing clarification questions',
];

const IMPLEMENTATION_PHASES = [
  {
    pending: 'Creating branch feature/export-pdf…',
    done: 'Created branch feature/export-pdf',
  },
  {
    pending: 'Analyzing codebase…',
    done: 'Found reports module and integration points',
  },
  {
    pending: 'Generating changes…',
    done: 'Generated code changes and updated contracts',
  },
  {
    pending: 'Running tests…',
    done: 'Tests completed successfully',
  },
];

const DEPLOY_PHASES = [
  {
    pending: 'Building project…',
    done: 'Build completed',
  },
  {
    pending: 'Starting container…',
    done: 'Container started',
  },
  {
    pending: 'Deploying to dev stand…',
    done: 'Dev stand updated',
  },
];

const INITIAL_ANSWERS = {
  audience: '',
  formats: [],
  constraints: '',
};

function shortTask(task) {
  const normalized = String(task || '').trim();
  if (normalized.length <= 86) {
    return normalized;
  }
  return `${normalized.slice(0, 83)}…`;
}

function normalizeFormats(formats) {
  if (!Array.isArray(formats) || formats.length === 0) {
    return 'PDF';
  }
  if (formats.length === 1) {
    return formats[0];
  }
  return `${formats.slice(0, -1).join(', ')} and ${formats[formats.length - 1]}`;
}

export default function ProductPipelineMvp() {
  const shellRef = useRef(null);
  const composerRef = useRef(null);
  const stopResetTimerRef = useRef(null);
  const [state, setState] = useState(APP_STATES.IDLE);
  const [task, setTask] = useState('');
  const [selectedProject, setSelectedProject] = useState(MOCK_PROJECTS[0]?.id || null);
  const [launching, setLaunching] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [stageReady, setStageReady] = useState(false);
  const [stageGapHeight, setStageGapHeight] = useState(null);
  const [answers, setAnswers] = useState(() => ({ ...INITIAL_ANSWERS }));
  const [implementationIndex, setImplementationIndex] = useState(0);
  const [deployIndex, setDeployIndex] = useState(0);
  const [bootIndex, setBootIndex] = useState(0);
  const [showPlanDetails, setShowPlanDetails] = useState(false);

  const clearRunProgress = useCallback(() => {
    setImplementationIndex(0);
    setDeployIndex(0);
    setBootIndex(0);
  }, []);

  const runSummary = useMemo(() => {
    const items = [];
    const trimmedTask = shortTask(task);
    if (trimmedTask) {
      items.push(trimmedTask);
    }
    if (answers.audience) {
      items.push(`Access: ${answers.audience.toLowerCase()}`);
    }
    items.push(`Export formats: ${normalizeFormats(answers.formats)}`);
    if (answers.constraints?.trim()) {
      items.push(answers.constraints.trim());
    } else {
      items.push('No extra constraints');
    }
    return items;
  }, [task, answers]);

  const planItems = useMemo(() => {
    const formats = normalizeFormats(answers.formats);
    return [
      'Add endpoint /reports/export',
      `Implement ${formats} generation`,
      'Add data processing service for large datasets',
      'Add unit and integration tests',
    ];
  }, [answers.formats]);

  const clarificationValid = useMemo(() => {
    const requiredAudience = answers.audience.trim().length > 0;
    const requiredFormats = Array.isArray(answers.formats) && answers.formats.length > 0;
    return requiredAudience && requiredFormats;
  }, [answers]);

  const isRunningFlow = launching || stopping || state !== APP_STATES.IDLE;
  const isStageVisible = stageReady && state !== APP_STATES.IDLE;

  const recalcStageGapHeight = useCallback(() => {
    if (!shellRef.current || !composerRef.current) {
      return;
    }
    const shellRect = shellRef.current.getBoundingClientRect();
    const composerRect = composerRef.current.getBoundingClientRect();
    const available = Math.floor(composerRect.top - shellRect.top);
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

  useEffect(() => {
    if (state !== APP_STATES.RUN_STARTED) {
      return undefined;
    }

    setBootIndex(0);
    let step = 0;
    const interval = window.setInterval(() => {
      step += 1;
      if (step < RUN_BOOT_PHASES.length) {
        setBootIndex(step);
        return;
      }
      window.clearInterval(interval);
      setState(APP_STATES.CLARIFICATION);
    }, 900);

    return () => window.clearInterval(interval);
  }, [state]);

  useEffect(() => {
    if (state !== APP_STATES.IMPLEMENTATION) {
      return undefined;
    }

    setShowPlanDetails(false);
    setImplementationIndex(0);
    let step = 0;
    let reviewTimer = null;
    const interval = window.setInterval(() => {
      step += 1;
      if (step < IMPLEMENTATION_PHASES.length) {
        setImplementationIndex(step);
        return;
      }
      window.clearInterval(interval);
      reviewTimer = window.setTimeout(() => {
        setState(APP_STATES.REVIEW);
      }, 700);
    }, 1200);

    return () => {
      window.clearInterval(interval);
      if (reviewTimer) {
        window.clearTimeout(reviewTimer);
      }
    };
  }, [state]);

  useEffect(() => {
    if (state !== APP_STATES.DEPLOY) {
      return undefined;
    }

    setDeployIndex(0);
    let step = 0;
    let doneTimer = null;
    const interval = window.setInterval(() => {
      step += 1;
      if (step < DEPLOY_PHASES.length) {
        setDeployIndex(step);
        return;
      }
      window.clearInterval(interval);
      doneTimer = window.setTimeout(() => {
        setState(APP_STATES.DONE);
      }, 700);
    }, 1200);

    return () => {
      window.clearInterval(interval);
      if (doneTimer) {
        window.clearTimeout(doneTimer);
      }
    };
  }, [state]);

  const startRun = () => {
    if (!task.trim()) {
      return;
    }

    if (stopResetTimerRef.current) {
      window.clearTimeout(stopResetTimerRef.current);
      stopResetTimerRef.current = null;
    }
    setStopping(false);
    clearRunProgress();
    setStageReady(false);
    setAnswers({ ...INITIAL_ANSWERS });
    setShowPlanDetails(false);
    setLaunching(true);

    window.setTimeout(() => {
      setLaunching(false);
      setState(APP_STATES.RUN_STARTED);
      window.setTimeout(() => {
        setStageReady(true);
      }, 1700);
    }, 600);
  };

  const stopRun = () => {
    if (stopping) {
      return;
    }
    if (stopResetTimerRef.current) {
      window.clearTimeout(stopResetTimerRef.current);
      stopResetTimerRef.current = null;
    }
    clearRunProgress();
    setStageReady(false);
    setLaunching(false);
    setShowPlanDetails(false);
    setAnswers({ ...INITIAL_ANSWERS });
    setState(APP_STATES.IDLE);
    setStopping(true);
    stopResetTimerRef.current = window.setTimeout(() => {
      setStopping(false);
      stopResetTimerRef.current = null;
    }, 2200);
  };

  const handleComposerEnter = (event) => {
    if (event.shiftKey) {
      return;
    }
    event.preventDefault();
    if (!isRunningFlow && task.trim() && !launching) {
      startRun();
    }
  };

  const goToConfirmation = () => {
    if (!clarificationValid) {
      return;
    }
    setState(APP_STATES.CONFIRMATION);
  };

  const confirmUnderstanding = () => {
    setState(APP_STATES.PLANNING);
  };

  const editClarification = () => {
    setState(APP_STATES.CLARIFICATION);
  };

  const approvePlan = () => {
    setState(APP_STATES.IMPLEMENTATION);
  };

  const requestPlanChange = () => {
    setState(APP_STATES.CLARIFICATION);
  };

  const approveReview = () => {
    setState(APP_STATES.DEPLOY);
  };

  const sendBackToRework = () => {
    clearRunProgress();
    setState(APP_STATES.IMPLEMENTATION);
  };

  const startOver = () => {
    if (stopResetTimerRef.current) {
      window.clearTimeout(stopResetTimerRef.current);
      stopResetTimerRef.current = null;
    }
    setTask('');
    clearRunProgress();
    setStageReady(false);
    setStopping(false);
    setAnswers({ ...INITIAL_ANSWERS });
    setLaunching(false);
    setShowPlanDetails(false);
    setState(APP_STATES.IDLE);
  };

  useEffect(() => () => {
    if (stopResetTimerRef.current) {
      window.clearTimeout(stopResetTimerRef.current);
    }
  }, []);

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

  const renderClarificationCenter = () => (
    <div className="vp-center-panel vp-center-panel-question">
      <Title level={4} className="vp-center-title">We need a few details</Title>
      <Text className="muted vp-center-subtitle">Answer the questions to continue implementation.</Text>

      <div className="vp-question-scroll">
        <div className="vp-question-list">
          <div className="vp-question">
            <Text className="vp-question-title">{CLARIFICATION_QUESTIONS.audience.title}<span className="vp-required-mark">*</span></Text>
            <Radio.Group
              value={answers.audience}
              onChange={(event) => setAnswers((prev) => ({ ...prev, audience: event.target.value }))}
              className="vp-radio-group"
            >
              {CLARIFICATION_QUESTIONS.audience.options.map((item) => (
                <Radio key={item} value={item}>{item}</Radio>
              ))}
            </Radio.Group>
          </div>

          <div className="vp-question">
            <Text className="vp-question-title">{CLARIFICATION_QUESTIONS.formats.title}<span className="vp-required-mark">*</span></Text>
            <Checkbox.Group
              value={answers.formats}
              onChange={(values) => setAnswers((prev) => ({ ...prev, formats: values }))}
              className="vp-checkbox-group"
            >
              {CLARIFICATION_QUESTIONS.formats.options.map((item) => (
                <Checkbox key={item} value={item}>{item}</Checkbox>
              ))}
            </Checkbox.Group>
          </div>

          <div className="vp-question">
            <Text className="vp-question-title">{CLARIFICATION_QUESTIONS.constraints.title}</Text>
            <TextArea
              value={answers.constraints}
              onChange={(event) => setAnswers((prev) => ({ ...prev, constraints: event.target.value }))}
              placeholder="For example: response time should stay below 2 seconds…"
              autoSize={{ minRows: 3, maxRows: 6 }}
            />
          </div>
        </div>
      </div>

      <div className="vp-actions-row">
        <Button type="primary" onClick={goToConfirmation} disabled={!clarificationValid}>Continue</Button>
      </div>
    </div>
  );

  const renderConfirmationCenter = () => (
    <div className="vp-center-panel vp-center-panel-question">
      <Title level={4} className="vp-center-title">Please confirm the understanding</Title>
      <ul className="vp-summary-list vp-question-scroll">
        {runSummary.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <div className="vp-actions-row">
        <Button onClick={editClarification}>Edit answers</Button>
        <Button type="primary" onClick={confirmUnderstanding}>Looks right</Button>
      </div>
    </div>
  );

  const renderPlanningCenter = () => (
    <div className="vp-center-panel vp-center-panel-question">
      <Title level={4} className="vp-center-title">Implementation plan is ready</Title>
      <ol className="vp-plan-list vp-question-scroll">
        {planItems.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ol>
      <Button
        type="text"
        className="vp-link-btn"
        onClick={() => setShowPlanDetails((value) => !value)}
      >
        {showPlanDetails ? 'Hide details' : 'Show details'}
      </Button>
      {showPlanDetails && (
        <div className="vp-plan-details-grid">
          <div>
            <Text strong>Files to change</Text>
            <ul>
              <li><code>backend/reports/controller.js</code></li>
              <li><code>backend/reports/pdf-service.js</code></li>
              <li><code>backend/reports/export.spec.js</code></li>
            </ul>
          </div>
          <div>
            <Text strong>Dependencies</Text>
            <ul>
              <li>PDF generation library</li>
              <li>Report aggregation service</li>
            </ul>
          </div>
          <div>
            <Text strong>Risks</Text>
            <ul>
              <li>Large datasets may increase render time</li>
              <li>Need memory guardrails for PDF generation</li>
            </ul>
          </div>
        </div>
      )}
      <div className="vp-actions-row">
        <Button onClick={requestPlanChange}>Change plan</Button>
        <Button type="primary" onClick={approvePlan}>Approve plan</Button>
      </div>
    </div>
  );

  const renderReviewCenter = () => (
    <div className="vp-center-panel vp-center-panel-question">
      <Title level={4} className="vp-center-title">Changes are ready for review</Title>
      <div className="vp-review-summary vp-question-scroll">
        <p>Added endpoint <code>/reports/export</code></p>
        <p>Implemented PDF generation</p>
        <p>Added export and pagination tests</p>
      </div>
      <div className="vp-diff-preview">
        <div>+ exportPdf(reportId)</div>
        <div>+ PdfService.js</div>
        <div>+ reports/export.spec.js</div>
      </div>
      <div className="vp-actions-row">
        <Button onClick={sendBackToRework}>Send back</Button>
        <Button type="primary" onClick={approveReview}>Looks good, create PR</Button>
      </div>
    </div>
  );

  const renderDoneCenter = () => (
    <div className="vp-center-panel vp-center-panel-question">
      <Title level={3} className="vp-center-title">Task delivered</Title>
      <div className="vp-review-summary">
        <p>Code is ready</p>
        <p>PR created</p>
        <p>Deploy completed</p>
      </div>
      <div className="vp-result-links">
        <Button type="primary" href="https://github.com/example/repo/pull/2481" target="_blank">
          Open PR
        </Button>
        <Button href="https://dev.app/export-test" target="_blank">
          Open dev
        </Button>
        <Button onClick={startOver}>New task</Button>
      </div>
    </div>
  );

  const renderCenterContent = () => {
    if (!isStageVisible) {
      return null;
    }

    if (state === APP_STATES.RUN_STARTED) {
      return renderStageSkeleton('Spinning up delivery pipeline', RUN_BOOT_PHASES[bootIndex] || RUN_BOOT_PHASES[0]);
    }

    if (state === APP_STATES.CLARIFICATION) {
      return renderClarificationCenter();
    }

    if (state === APP_STATES.CONFIRMATION) {
      return renderConfirmationCenter();
    }

    if (state === APP_STATES.PLANNING) {
      return renderPlanningCenter();
    }

    if (state === APP_STATES.IMPLEMENTATION) {
      return renderStageSkeleton('Implementing changes', IMPLEMENTATION_PHASES[implementationIndex]?.pending || 'Generating changes…');
    }

    if (state === APP_STATES.REVIEW) {
      return renderReviewCenter();
    }

    if (state === APP_STATES.DEPLOY) {
      return renderStageSkeleton('Deploying to dev stand', DEPLOY_PHASES[deployIndex]?.pending || 'Deploying…');
    }

    return renderDoneCenter();
  };

  return (
    <div className={`vp-pipeline-shell ${isRunningFlow ? 'is-running' : ''}`} ref={shellRef}>
      <div className="vp-center-zone">
        <div className={`vp-entry-headline ${isRunningFlow ? 'is-hidden' : ''}`}>
          <Title level={2} className="vp-entry-title">What are we inventing today?</Title>
          <Text className="vp-entry-subtitle">
            Describe the business request and the system will drive it to a dev-ready result.
          </Text>
        </div>

        <div
          className={`vp-stage-center ${isStageVisible ? 'is-visible' : ''}`}
          style={isRunningFlow && stageGapHeight !== null ? { '--vp-stage-gap-height': `${stageGapHeight}px` } : undefined}
        >
          {renderCenterContent()}
        </div>

        <div className={`vp-composer-zone ${stopping ? 'is-returning' : (isRunningFlow ? 'is-docked' : 'is-idle')}`} ref={composerRef}>
          <div className={`vp-composer-shell ${isRunningFlow ? 'is-locked' : ''}`}>
            <TextArea
              value={task}
              onChange={(event) => setTask(event.target.value)}
              onPressEnter={handleComposerEnter}
              placeholder="Ask anything"
              autoSize={{ minRows: 2, maxRows: 2 }}
              className="vp-entry-input"
              disabled={isRunningFlow}
            />

            <div className="vp-composer-controls">
              <div className="vp-tool-row">
                {MOCK_PROJECTS.length > 1 && (
                  <div className="vp-select-shell">
                    <AppstoreOutlined />
                    <Select
                      value={selectedProject}
                      options={MOCK_PROJECTS.map((item) => ({ value: item.id, label: item.name }))}
                      onChange={setSelectedProject}
                      style={{ width: '100%' }}
                      className="vp-project-select"
                      variant="borderless"
                      disabled={isRunningFlow}
                    />
                  </div>
                )}
              </div>

              <Button
                type="primary"
                shape="circle"
                icon={isRunningFlow ? <CloseCircleFilled /> : <CaretRightOutlined />}
                className="vp-run-icon-btn"
                onClick={isRunningFlow ? stopRun : startRun}
                disabled={!task.trim() && !isRunningFlow}
                aria-label={isRunningFlow ? 'Stop' : 'Run'}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
