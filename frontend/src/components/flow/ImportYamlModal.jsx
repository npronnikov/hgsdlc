import { useRef, useState } from 'react';
import { Button, Input, message, Modal, Typography } from 'antd';
import { UploadOutlined } from '@ant-design/icons';

const { Text } = Typography;

export function ImportYamlModal({ open, onClose, onImport }) {
  const [yamlText, setYamlText] = useState('');
  const fileInputRef = useRef(null);

  const handleConfirm = () => {
    if (!yamlText.trim()) {
      return;
    }
    const ok = onImport(yamlText);
    if (ok) {
      setYamlText('');
      onClose();
    }
  };

  const handleCancel = () => {
    setYamlText('');
    onClose();
  };

  const handleFile = (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result;
      if (typeof text === 'string') {
        setYamlText(text);
      }
    };
    reader.onerror = () => {
      message.error('Failed to read file');
    };
    reader.readAsText(file);
    event.target.value = '';
  };

  return (
    <Modal
      title="Import flow from YAML"
      open={open}
      onCancel={handleCancel}
      onOk={handleConfirm}
      okText="Import"
      cancelText="Cancel"
      okButtonProps={{ disabled: !yamlText.trim() }}
      width={720}
    >
      <div style={{ display: 'grid', gap: 12 }}>
        <Text type="secondary">
          Paste YAML content or upload a .yaml file. This will replace the current nodes and flow metadata.
        </Text>
        <div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".yaml,.yml"
            onChange={handleFile}
            style={{ display: 'none' }}
          />
          <Button
            icon={<UploadOutlined />}
            onClick={() => fileInputRef.current?.click()}
          >
            Upload .yaml file
          </Button>
        </div>
        <Input.TextArea
          rows={18}
          value={yamlText}
          onChange={(e) => setYamlText(e.target.value)}
          placeholder="Paste flow YAML here..."
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </div>
    </Modal>
  );
}
