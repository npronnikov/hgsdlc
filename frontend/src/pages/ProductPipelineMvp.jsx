import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Checkbox, Input, Modal, Radio, Select, Spin, Tag, Typography } from 'antd';
import { AppstoreOutlined, ArrowUpOutlined } from '@ant-design/icons';

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

const PIPELINE_STEPS = [
  { key: 'request', label: 'Request accepted' },
  { key: 'clarification', label: 'Clarification' },
  { key: 'planning', label: 'Planning' },
  { key: 'implementation', label: 'Implementation' },
  { key: 'review', label: 'Review' },
  { key: 'deploy', label: 'Deploy to dev' },
];

const STATE_TO_PIPELINE_INDEX = {
  [APP_STATES.RUN_STARTED]: 0,
  [APP_STATES.CLARIFICATION]: 1,
  [APP_STATES.CONFIRMATION]: 1,
  [APP_STATES.PLANNING]: 2,
  [APP_STATES.IMPLEMENTATION]: 3,
  [APP_STATES.REVIEW]: 4,
  [APP_STATES.DEPLOY]: 5,
  [APP_STATES.DONE]: 5,
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
  const [state, setState] = useState(APP_STATES.IDLE);
  const [task, setTask] = useState('');
  const [selectedProject, setSelectedProject] = useState(MOCK_PROJECTS[0]?.id || null);
  const [launching, setLaunching] = useState(false);
  const [answers, setAnswers] = useState(() => ({ ...INITIAL_ANSWERS }));
  const [activity, setActivity] = useState([]);
  const [implementationIndex, setImplementationIndex] = useState(0);
  const [deployIndex, setDeployIndex] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [showPlanDetails, setShowPlanDetails] = useState(false);

  const appendLog = useCallback((text, tone = 'default') => {
    setActivity((current) => [
      ...current,
      {
        id: `${Date.now()}-${Math.random()}`,
        text,
        tone,
      },
    ]);
  }, []);

  const clearRunProgress = useCallback(() => {
    setImplementationIndex(0);
    setDeployIndex(0);
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

  const pipelineIndex = state === APP_STATES.IDLE ? -1 : STATE_TO_PIPELINE_INDEX[state] ?? -1;
  const isInputState = [APP_STATES.CLARIFICATION, APP_STATES.CONFIRMATION, APP_STATES.PLANNING, APP_STATES.REVIEW].includes(state);

  useEffect(() => {
    if (isInputState) {
      setDialogOpen(true);
      if (state === APP_STATES.CLARIFICATION) {
        appendLog('⏳ Human input required: clarification', 'pending');
      }
      if (state === APP_STATES.CONFIRMATION) {
        appendLog('⏳ Human input required: confirm understanding', 'pending');
      }
      if (state === APP_STATES.PLANNING) {
        appendLog('⏳ Human input required: approve plan', 'pending');
      }
      if (state === APP_STATES.REVIEW) {
        appendLog('⏳ Human input required: review changes', 'pending');
      }
    } else {
      setDialogOpen(false);
    }
  }, [isInputState, state, appendLog]);

  useEffect(() => {
    if (state !== APP_STATES.RUN_STARTED) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      appendLog('✔ Clarification questions generated', 'success');
      setState(APP_STATES.CLARIFICATION);
    }, 900);
    return () => window.clearTimeout(timer);
  }, [state, appendLog]);

  useEffect(() => {
    if (state !== APP_STATES.IMPLEMENTATION) {
      return undefined;
    }
    setShowPlanDetails(false);
    setImplementationIndex(0);
    appendLog(`⏳ ${IMPLEMENTATION_PHASES[0].pending}`, 'pending');
    let step = 0;
    let reviewTimer = null;
    const interval = window.setInterval(() => {
      appendLog(`✔ ${IMPLEMENTATION_PHASES[step].done}`, 'success');
      step += 1;
      if (step < IMPLEMENTATION_PHASES.length) {
        setImplementationIndex(step);
        appendLog(`⏳ ${IMPLEMENTATION_PHASES[step].pending}`, 'pending');
        return;
      }
      window.clearInterval(interval);
      reviewTimer = window.setTimeout(() => {
        appendLog('✔ Changes are ready for review', 'success');
        setState(APP_STATES.REVIEW);
      }, 700);
    }, 1200);

    return () => {
      window.clearInterval(interval);
      if (reviewTimer) {
        window.clearTimeout(reviewTimer);
      }
    };
  }, [state, appendLog]);

  useEffect(() => {
    if (state !== APP_STATES.DEPLOY) {
      return undefined;
    }
    setDeployIndex(0);
    appendLog(`⏳ ${DEPLOY_PHASES[0].pending}`, 'pending');
    let step = 0;
    let doneTimer = null;
    const interval = window.setInterval(() => {
      appendLog(`✔ ${DEPLOY_PHASES[step].done}`, 'success');
      step += 1;
      if (step < DEPLOY_PHASES.length) {
        setDeployIndex(step);
        appendLog(`⏳ ${DEPLOY_PHASES[step].pending}`, 'pending');
        return;
      }
      window.clearInterval(interval);
      doneTimer = window.setTimeout(() => {
        appendLog('✔ PR created', 'success');
        appendLog('✔ Deploy to dev completed', 'success');
        setState(APP_STATES.DONE);
      }, 700);
    }, 1200);

    return () => {
      window.clearInterval(interval);
      if (doneTimer) {
        window.clearTimeout(doneTimer);
      }
    };
  }, [state, appendLog]);

  const startRun = () => {
    const trimmedTask = task.trim();
    if (!trimmedTask) {
      return;
    }
    clearRunProgress();
    setAnswers({ ...INITIAL_ANSWERS });
    setActivity([]);
    setLaunching(true);
    window.setTimeout(() => {
      setLaunching(false);
      setState(APP_STATES.RUN_STARTED);
      appendLog('✔ Request accepted', 'success');
      appendLog('✔ Workflow selected: Feature delivery', 'success');
      if (selectedProject) {
        appendLog(`✔ Project selected: ${MOCK_PROJECTS.find((item) => item.id === selectedProject)?.name || selectedProject}`, 'success');
      }
    }, 750);
  };

  const goToConfirmation = () => {
    if (!clarificationValid) {
      return;
    }
    appendLog('✔ Clarification answers received', 'success');
    setState(APP_STATES.CONFIRMATION);
  };

  const confirmUnderstanding = () => {
    appendLog('✔ Understanding confirmed', 'success');
    setState(APP_STATES.PLANNING);
  };

  const editClarification = () => {
    appendLog('↩ Returned to clarification', 'default');
    setState(APP_STATES.CLARIFICATION);
  };

  const approvePlan = () => {
    appendLog('✔ Implementation plan approved', 'success');
    setState(APP_STATES.IMPLEMENTATION);
  };

  const requestPlanChange = () => {
    appendLog('↩ Plan returned for updates', 'default');
    setState(APP_STATES.CLARIFICATION);
  };

  const approveReview = () => {
    appendLog('✔ Review approved, starting deploy', 'success');
    setState(APP_STATES.DEPLOY);
  };

  const sendBackToRework = () => {
    clearRunProgress();
    appendLog('↩ Sent back for rework', 'default');
    setState(APP_STATES.IMPLEMENTATION);
  };

  const startOver = () => {
    setTask('');
    clearRunProgress();
    setAnswers({ ...INITIAL_ANSWERS });
    setActivity([]);
    setLaunching(false);
    setState(APP_STATES.IDLE);
  };

  const renderEntry = () => (
    <div className="vp-entry-screen">
      <div className="vp-entry-card">
        <Title level={2} className="vp-entry-title">What are we inventing today?</Title>
        <Text className="vp-entry-subtitle">
          Describe the business request and the system will drive it to a dev-ready result.
        </Text>
        <div className="vp-composer-shell">
          <TextArea
            value={task}
            onChange={(event) => setTask(event.target.value)}
            placeholder="Ask anything"
            autoSize={{ minRows: 2, maxRows: 2 }}
            className="vp-entry-input"
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
                  />
                </div>
              )}
            </div>
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowUpOutlined />}
              className="vp-run-icon-btn"
              onClick={startRun}
              disabled={!task.trim()}
              loading={launching}
              aria-label="Run"
            />
          </div>
        </div>
      </div>
    </div>
  );

  const renderClarificationDialog = () => (
    <>
      <Title level={4} style={{ marginTop: 0 }}>We need a few details</Title>
      <Text className="muted">
        Answer a few questions so implementation is correct.
      </Text>
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
    </>
  );

  const renderConfirmationDialog = () => (
    <>
      <Title level={4} style={{ marginTop: 0 }}>We understood the task as:</Title>
      <ul className="vp-summary-list">
        {runSummary.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </>
  );

  const renderPlanningDialog = () => (
    <>
      <Title level={4} style={{ marginTop: 0 }}>Implementation plan is ready</Title>
      <ol className="vp-plan-list">
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
    </>
  );

  const renderImplementation = () => (
    <div className="vp-work-content">
      <Title level={4} style={{ marginTop: 0 }}>Implementing changes</Title>
      <div className="vp-loader-box">
        <Spin />
        <div className="vp-loader-lines">
          <Text>{IMPLEMENTATION_PHASES[implementationIndex]?.pending || 'Generating changes…'}</Text>
        </div>
      </div>
    </div>
  );

  const renderReviewDialog = () => (
    <>
      <Title level={4} style={{ marginTop: 0 }}>Changes are ready for review</Title>
      <div className="vp-review-summary">
        <p>Added endpoint <code>/reports/export</code></p>
        <p>Implemented PDF generation</p>
        <p>Added export and pagination tests</p>
      </div>
      <div className="vp-diff-preview">
        <div>+ exportPdf(reportId)</div>
        <div>+ PdfService.js</div>
        <div>+ reports/export.spec.js</div>
      </div>
    </>
  );

  const renderDeploy = () => (
    <div className="vp-work-content">
      <Title level={4} style={{ marginTop: 0 }}>Deploying to dev stand</Title>
      <div className="vp-loader-box">
        <Spin />
        <div className="vp-loader-lines">
          <Text>{DEPLOY_PHASES[deployIndex]?.pending || 'Deploying…'}</Text>
        </div>
      </div>
    </div>
  );

  const renderDone = () => (
    <div className="vp-work-content">
      <Title level={3} style={{ marginTop: 0 }}>Task delivered</Title>
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

  const renderRunContent = () => {
    if (isInputState) {
      return (
        <div className="vp-work-content">
          <Title level={4} style={{ marginTop: 0 }}>Waiting for your input</Title>
          <Text className="muted">
            This step requires your decision. Open the dialog to continue.
          </Text>
          <div className="vp-actions-row">
            <Button type="primary" onClick={() => setDialogOpen(true)}>Open dialog</Button>
          </div>
        </div>
      );
    }
    if (state === APP_STATES.IMPLEMENTATION) return renderImplementation();
    if (state === APP_STATES.DEPLOY) return renderDeploy();
    if (state === APP_STATES.DONE) return renderDone();

    return (
      <div className="vp-work-content">
        <Title level={4} style={{ marginTop: 0 }}>Request accepted</Title>
        <div className="vp-loader-box">
          <Spin />
          <Text className="muted">Preparing execution steps…</Text>
        </div>
      </div>
    );
  };

  if (state === APP_STATES.IDLE) {
    return renderEntry();
  }

  return (
    <div className="vp-run-page">
      <div className="vp-run-grid">
        <div className="vp-main-column">
          <Card className="vp-pipeline-card vp-surface-card" bordered={false}>
            <div className="vp-task-header">
              <Text className="vp-task-label">Task</Text>
              {selectedProject && (
                <Tag bordered={false} color="processing">
                  {MOCK_PROJECTS.find((item) => item.id === selectedProject)?.name || selectedProject}
                </Tag>
              )}
            </div>
            <Title level={4} className="vp-task-title">"{shortTask(task)}"</Title>
            <div className="vp-timeline">
              {PIPELINE_STEPS.map((item, index) => {
                const isDone = state === APP_STATES.DONE;
                let status = 'pending';
                if (isDone || index < pipelineIndex) {
                  status = 'completed';
                } else if (index === pipelineIndex) {
                  status = 'active';
                }
                return (
                  <div key={item.key} className={`vp-timeline-step vp-timeline-step-${status}`}>
                    <div className="vp-timeline-node-wrap">
                      <span className={`vp-timeline-dot ${status === 'active' ? 'is-pulsing' : ''}`} />
                      {index < PIPELINE_STEPS.length - 1 && <span className="vp-timeline-line" />}
                    </div>
                    <span className="vp-timeline-label">{item.label}</span>
                  </div>
                );
              })}
            </div>
          </Card>

          <Card className="vp-work-card vp-surface-card" bordered={false}>
            {renderRunContent()}
          </Card>
        </div>

        <Card className="vp-log-card vp-surface-card" bordered={false} title="Activity log">
          <div className="vp-log-list">
            {activity.map((item) => (
              <div key={item.id} className={`vp-log-item tone-${item.tone}`}>
                <span className="vp-log-dot" />
                <span>{item.text}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
      <Modal
        title={state === APP_STATES.CLARIFICATION
          ? 'Clarification'
          : state === APP_STATES.CONFIRMATION
            ? 'Confirm understanding'
            : state === APP_STATES.PLANNING
              ? 'Planning'
              : 'Review'}
        open={dialogOpen && isInputState}
        onCancel={() => setDialogOpen(false)}
        className="vp-input-modal"
        width={760}
        footer={state === APP_STATES.CLARIFICATION
          ? [
            <Button key="continue" type="primary" onClick={goToConfirmation} disabled={!clarificationValid}>
              Continue
            </Button>,
          ]
          : state === APP_STATES.CONFIRMATION
            ? [
              <Button key="fix" onClick={editClarification}>Edit answers</Button>,
              <Button key="ok" type="primary" onClick={confirmUnderstanding}>Looks right</Button>,
            ]
            : state === APP_STATES.PLANNING
              ? [
                <Button key="change" onClick={requestPlanChange}>Change plan</Button>,
                <Button key="approve" type="primary" onClick={approvePlan}>Approve plan</Button>,
              ]
              : [
                <Button key="rework" onClick={sendBackToRework}>Send back</Button>,
                <Button key="approve-review" type="primary" onClick={approveReview}>Looks good, create PR</Button>,
              ]}
      >
        {state === APP_STATES.CLARIFICATION && renderClarificationDialog()}
        {state === APP_STATES.CONFIRMATION && renderConfirmationDialog()}
        {state === APP_STATES.PLANNING && renderPlanningDialog()}
        {state === APP_STATES.REVIEW && renderReviewDialog()}
      </Modal>
    </div>
  );
}
