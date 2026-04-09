import { Button, Tag } from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import HumanFormViewer, { isHumanForm } from '../HumanFormViewer.jsx';
import { useGateInput } from '../../hooks/useGateInput.js';
import { useThemeMode } from '../../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../../utils/monacoTheme.js';

export function GateInputPanel({ gate, onSubmitted }) {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const {
    editableArtifacts, editedByPath, updateArtifact,
    submitting, submitInput,
  } = useGateInput(gate, { onSubmitted });

  return (
    <div className="gate-input-panel">
      <div className="gate-input-header">
        <WarningOutlined className="gate-input-icon" />
        <span>Input Required</span>
        <Tag>{gate?.node_id}</Tag>
      </div>
      <div className="gate-input-body">
        {gate?.payload?.user_instructions && (
          <div className="gate-input-instructions">{gate.payload.user_instructions}</div>
        )}
        {editableArtifacts.map((artifact) => {
          const content = editedByPath[artifact.path] ?? artifact.content ?? '';
          const formJson = isHumanForm(content);
          return (
            <div key={artifact.path} className="gate-input-artifact">
              <div className="gate-input-artifact-path">{artifact.path}</div>
              {formJson
                ? (
                  <div className="gate-input-form-wrap">
                    <HumanFormViewer formJson={formJson} onChange={(v) => updateArtifact(artifact.path, v)} />
                  </div>
                )
                : (
                  <div className="gate-input-editor-wrap">
                    <Editor
                      height="100%"
                      language="markdown"
                      theme={monacoTheme}
                      value={content}
                      onChange={(v) => updateArtifact(artifact.path, v ?? '')}
                      options={{ minimap: { enabled: false }, wordWrap: 'on' }}
                    />
                  </div>
                )}
            </div>
          );
        })}
      </div>
      <div className="gate-input-footer">
        <Button type="primary" loading={submitting} onClick={submitInput}>
          Submit
        </Button>
      </div>
    </div>
  );
}
