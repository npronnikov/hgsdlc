import React from 'react';
import { Checkbox, Form, Input, InputNumber, Radio, Select, Typography } from 'antd';

const { Text, Title } = Typography;

export function isHumanForm(jsonString) {
  if (!jsonString) return null;
  try {
    const parsed = JSON.parse(jsonString);
    return parsed?.kind === 'human-form' ? parsed : null;
  } catch {
    return null;
  }
}

export function validateHumanForm(form) {
  if (!form) return null;
  const missing = (form.fields || []).filter((f) => {
    if (!f.required) return false;
    if (f.value === null || f.value === undefined || f.value === '') return true;
    if (Array.isArray(f.value) && f.value.length === 0) return true;
    return false;
  });
  if (missing.length === 0) return null;
  return `Required: ${missing.map((f) => f.label || f.id).join(', ')}`;
}

function FieldInput({ field, onChange, readOnly }) {
  const { id, type, options = [], value } = field;

  if (readOnly) {
    const display = Array.isArray(value) ? value.join(', ') : (value ?? '—');
    return <Text>{String(display)}</Text>;
  }

  switch (type) {
    case 'textarea':
      return (
        <Input.TextArea
          rows={4}
          value={value || ''}
          onChange={(e) => onChange(id, e.target.value)}
        />
      );
    case 'radio':
      return (
        <Radio.Group
          value={value ?? null}
          onChange={(e) => onChange(id, e.target.value)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
        >
          {options.map((opt) => (
            <Radio key={opt} value={opt}>{opt}</Radio>
          ))}
        </Radio.Group>
      );
    case 'checkbox':
      return (
        <Checkbox.Group
          value={value ?? []}
          onChange={(vals) => onChange(id, vals)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
        >
          {options.map((opt) => (
            <Checkbox key={opt} value={opt}>{opt}</Checkbox>
          ))}
        </Checkbox.Group>
      );
    case 'select':
      return (
        <Select
          style={{ width: '100%' }}
          value={value ?? undefined}
          onChange={(val) => onChange(id, val)}
          options={options.map((opt) => ({ value: opt, label: opt }))}
        />
      );
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          value={value ?? null}
          onChange={(val) => onChange(id, val)}
        />
      );
    default:
      return (
        <Input
          value={value || ''}
          onChange={(e) => onChange(id, e.target.value)}
        />
      );
  }
}

export default function HumanFormViewer({ formJson, onChange, readOnly = false }) {
  const fields = formJson?.fields || [];

  const handleChange = (fieldId, value) => {
    if (!onChange) return;
    const updated = {
      ...formJson,
      fields: fields.map((f) => (f.id === fieldId ? { ...f, value } : f)),
    };
    onChange(JSON.stringify(updated, null, 2));
  };

  return (
    <div style={{ padding: '4px 0' }}>
      {formJson?.title && (
        <Title level={5} style={{ marginTop: 0, marginBottom: 16 }}>
          {formJson.title}
        </Title>
      )}
      <Form layout="vertical" style={{ marginBottom: 0 }}>
        {fields.map((field) => (
          <Form.Item
            key={field.id}
            label={<Text strong>{field.label || field.id}</Text>}
            required={!readOnly && field.required}
            style={{ marginBottom: 16 }}
          >
            <FieldInput field={field} onChange={handleChange} readOnly={readOnly} />
          </Form.Item>
        ))}
      </Form>
    </div>
  );
}
