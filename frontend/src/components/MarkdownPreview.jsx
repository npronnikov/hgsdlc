import React, { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

let mermaidModulePromise = null;
let mermaidConfigured = false;

function formatMermaidError(error) {
  const raw = error instanceof Error ? error.message : String(error ?? 'Unknown Mermaid error');
  if (/syntax error in text/i.test(raw)) {
    return 'Mermaid syntax error. Check diagram definition.';
  }
  return raw;
}

async function getMermaid() {
  if (!mermaidModulePromise) {
    mermaidModulePromise = import('mermaid').then((module) => module.default);
  }
  const mermaid = await mermaidModulePromise;
  if (!mermaidConfigured) {
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      suppressErrorRendering: true,
    });
    // Keep parser errors local to the component; do not render global Mermaid error output.
    mermaid.setParseErrorHandler(() => {});
    mermaidConfigured = true;
  }
  return mermaid;
}

function MermaidBlock({ chart }) {
  const containerRef = useRef(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;

    const renderChart = async () => {
      if (!containerRef.current) {
        return;
      }
      containerRef.current.innerHTML = '';
      setError('');
      try {
        const mermaid = await getMermaid();
        const id = `gate-mermaid-${Date.now()}-${Math.random().toString(16).slice(2)}`;
        const { svg, bindFunctions } = await mermaid.render(id, chart);
        if (cancelled || !containerRef.current) {
          return;
        }
        containerRef.current.innerHTML = svg;
        if (typeof bindFunctions === 'function') {
          bindFunctions(containerRef.current);
        }
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(formatMermaidError(err));
      }
    };

    void renderChart();
    return () => {
      cancelled = true;
    };
  }, [chart]);

  if (error) {
    return (
      <div className="human-gate-mermaid-error">
        <pre className="frontmatter-block" style={{ marginBottom: 8 }}>{chart}</pre>
        <p className="human-gate-mermaid-error-text">{error}</p>
      </div>
    );
  }

  return <div ref={containerRef} className="human-gate-mermaid" />;
}

export default function MarkdownPreview({ markdown = '', className = '' }) {
  const content = useMemo(() => String(markdown ?? ''), [markdown]);
  const classes = ['markdown-preview', className].filter(Boolean).join(' ');

  return (
    <div className={classes}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ inline, node, className: codeClassName, children, ...props }) {
            const languageMatch = /language-([a-z0-9_-]+)/i.exec(codeClassName || '');
            const language = (languageMatch?.[1] || '').toLowerCase();
            const value = String(children ?? '').replace(/\n$/, '');
            const spansMultipleLines = !!(
              node?.position?.start?.line
              && node?.position?.end?.line
              && node.position.end.line > node.position.start.line
            );
            const isInlineCode = inline === true || (inline == null && !codeClassName && !spansMultipleLines);

            if (!isInlineCode && language === 'mermaid') {
              return <MermaidBlock chart={value} />;
            }
            if (!isInlineCode) {
              return (
                <pre className="human-gate-markdown-code">
                  <code className={codeClassName} {...props}>{value}</code>
                </pre>
              );
            }
            return <code className={`human-gate-markdown-inline-code ${codeClassName || ''}`.trim()} {...props}>{value}</code>;
          },
          a({ href, children, ...props }) {
            return (
              <a href={href} target="_blank" rel="noreferrer" {...props}>
                {children}
              </a>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
