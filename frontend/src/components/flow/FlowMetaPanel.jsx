import { Card, Divider, Input, List, Select, Switch, Typography } from 'antd';

const { Title, Text } = Typography;

const platformOptions = [
  { value: 'FRONT', label: 'Frontend' },
  { value: 'BACK', label: 'Backend' },
  { value: 'DATA', label: 'Data' },
];
const flowKindOptions = [
  { value: 'analysis', label: 'Analysis' },
  { value: 'code', label: 'Code' },
  { value: 'delivery', label: 'Delivery' },
  { value: 'full-cycle', label: 'Full Cycle' },
];
const riskLevelOptions = [
  { value: 'low', label: 'low' },
  { value: 'medium', label: 'medium' },
  { value: 'high', label: 'high' },
  { value: 'critical', label: 'critical' },
];
const scopeOptions = [
  { value: 'organization', label: 'Organization' },
  { value: 'team', label: 'Team' },
];

const requiredLabel = (label) => `${label} *`;

export function FlowMetaPanel({ editor }) {
  const {
    flowMeta, updateFlowMeta, handleFlowIdChange, handleScopeChange, handleStartNodeChange,
    isReadOnly, isCreateMode, versionOptions, nodes,
    ruleOptions, rulesCatalog,
    flowVersion, handleVersionSelect, isEditing,
  } = editor;

  const renderVersionSelector = () => {
    if (flowMeta.flowId) {
      return (
        <Select
          value={flowVersion || undefined}
          options={versionOptions}
          onChange={(value) => handleVersionSelect(value)}
          className="rule-version-select"
          placeholder="Version"
          disabled={isEditing}
        />
      );
    }
    return <span className="rule-version-pill">new</span>;
  };

  return (
    <Card className="flow-panel-card">
      <div className="rule-fields-header">
        <Title level={5} style={{ margin: 0 }}>Flow data</Title>
        {renderVersionSelector()}
      </div>
      <div className="form-stack">
        <div>
          <Text className="muted">{requiredLabel('Coding agent')}</Text>
          <div className="field-control">
            <Select
              value={flowMeta.codingAgent}
              disabled={isReadOnly}
              onChange={(value) => updateFlowMeta({ codingAgent: value })}
              options={[
                { value: 'qwen', label: 'qwen' },
                { value: 'gigacode', label: 'gigacode' },
                { value: 'claude', label: 'claude' },
              ]}
              placeholder="Select coding agent"
              title="Which coding agent this flow is executed with."
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Name')}</Text>
          <div className="field-control">
            <Input
              value={flowMeta.title}
              disabled={isReadOnly}
              onChange={(event) => updateFlowMeta({ title: event.target.value })}
              title="Short display name of the flow."
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('ID Flow')}</Text>
          <div className="field-control">
            <Input
              value={flowMeta.flowId}
              disabled={isReadOnly || !isCreateMode || versionOptions.length > 0}
              onChange={(event) => handleFlowIdChange(event.target.value)}
              title="Stable flow identifier used for canonical_name and references."
            />
          </div>
        </div>
        <div>
          <Text className="muted">Description</Text>
          <div className="field-control">
            <Input.TextArea
              rows={3}
              value={flowMeta.description}
              disabled={isReadOnly}
              onChange={(event) => updateFlowMeta({ description: event.target.value })}
              title="Short description of the flow scenario and purpose."
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Team code')}</Text>
          <div className="field-control">
            <Input
              value={flowMeta.teamCode}
              disabled={isReadOnly}
              onChange={(event) => updateFlowMeta({ teamCode: event.target.value })}
              title="Owner team code for this flow."
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Scope')}</Text>
          <div className="field-control">
            <Select
              value={flowMeta.scope || undefined}
              disabled={isReadOnly}
              onChange={handleScopeChange}
              options={scopeOptions}
              placeholder="Select scope"
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Platform')}</Text>
          <div className="field-control">
            <Select
              value={flowMeta.platformCode || undefined}
              disabled={isReadOnly}
              onChange={(value) => updateFlowMeta({ platformCode: value })}
              options={platformOptions}
              placeholder="Select platform"
              title="Flow target platform: FRONT, BACK, or DATA."
            />
          </div>
        </div>
        <div>
          <Text className="muted">Tags</Text>
          <div className="field-control">
            <Select
              mode="tags"
              value={flowMeta.tags || []}
              disabled={isReadOnly}
              onChange={(value) => updateFlowMeta({ tags: value })}
              placeholder="Add tags"
              title="Tags used to filter and search flows."
            />
          </div>
        </div>
        <div>
          <Text className="muted">Flow kind</Text>
          <div className="field-control">
            <Select
              value={flowMeta.flowKind || undefined}
              disabled={isReadOnly}
              onChange={(value) => updateFlowMeta({ flowKind: value })}
              options={flowKindOptions}
              placeholder="Select flow kind"
              title="Flow type: analysis, code, delivery, or full-cycle."
            />
          </div>
        </div>
        <div>
          <Text className="muted">Risk level</Text>
          <div className="field-control">
            <Select
              value={flowMeta.riskLevel || undefined}
              disabled={isReadOnly}
              onChange={(value) => updateFlowMeta({ riskLevel: value })}
              options={riskLevelOptions}
              placeholder="Select risk level"
              title="Execution risk level for this flow."
            />
          </div>
        </div>
        {!isCreateMode && (
          <div>
            <Text className="muted">Publication status</Text>
            <div className="mono">{flowMeta.publicationStatus || 'draft'}</div>
          </div>
        )}
        <div>
          <Text className="muted">{requiredLabel('Start node')}</Text>
          <div className="field-control">
            <Select
              value={flowMeta.startNodeId}
              disabled={isReadOnly}
              onChange={handleStartNodeChange}
              options={nodes.map((node) => ({ value: node.id, label: node.id }))}
              title="Node where flow execution starts."
            />
          </div>
        </div>
      </div>

      <Divider />

      <div className="linked-block">
        <div className="linked-header">
          <Title level={5}>Linked rules</Title>
        </div>
        <Select
          mode="multiple"
          options={ruleOptions}
          value={flowMeta.ruleRefs}
          disabled={isReadOnly}
          onChange={(value) => updateFlowMeta({ ruleRefs: value })}
          placeholder="Select rules"
        />
        <List
          dataSource={flowMeta.ruleRefs}
          locale={{ emptyText: 'No linked rules' }}
          renderItem={(ref) => {
            const rule = rulesCatalog.find((item) => item.canonical === ref);
            const description = rule?.description || '';
            const snippet = description.length > 80
              ? `${description.slice(0, 80)}...`
              : description;
            return (
              <List.Item className="linked-item">
                <div className="linked-rule-row">
                  <span className="mono linked-rule-name">{ref}</span>
                </div>
                {snippet && (
                  <Text type="secondary" className="linked-rule-description">
                    {snippet}
                  </Text>
                )}
              </List.Item>
            );
          }}
        />
      </div>

      <Divider />

      <div className="form-stack">
        <div className="switch-row">
          <Text className="muted">Stop Flow if expected output is missing</Text>
          <Switch
            checked={flowMeta.failOnMissingDeclaredOutput}
            disabled={isReadOnly}
            onChange={(checked) => updateFlowMeta({ failOnMissingDeclaredOutput: checked })}
          />
        </div>
        <div className="switch-row">
          <Text className="muted">Stop Flow if expected mutation is missing</Text>
          <Switch
            checked={flowMeta.failOnMissingExpectedMutation}
            disabled={isReadOnly}
            onChange={(checked) => updateFlowMeta({ failOnMissingExpectedMutation: checked })}
          />
        </div>
        <div>
          <Text className="muted">Response schema (optional)</Text>
          <div className="field-control">
            <Input.TextArea
              rows={6}
              value={flowMeta.responseSchema}
              placeholder="YAML/JSON schema"
              disabled={isReadOnly}
              onChange={(event) => updateFlowMeta({ responseSchema: event.target.value })}
            />
          </div>
        </div>
      </div>
    </Card>
  );
}
