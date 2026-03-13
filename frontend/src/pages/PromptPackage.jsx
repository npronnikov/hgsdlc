import React from 'react';
import { Card, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title } = Typography;

export default function PromptPackage() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Prompt Package</Title>
        <StatusTag value="AI" />
      </div>
      <Card>
        <div className="prompt-section">
          <div className="prompt-title">system_header</div>
          <pre className="code-block">You are a coding agent...</pre>
        </div>
        <div className="prompt-section">
          <div className="prompt-title">project_context</div>
          <pre className="code-block">context_root_dir: src/main/java</pre>
        </div>
      </Card>
    </div>
  );
}
