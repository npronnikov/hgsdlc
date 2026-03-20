export const HG_MONACO_LIGHT = 'hg-light';
export const HG_MONACO_DARK = 'hg-dark';

export function configureMonacoThemes(monaco) {
  if (!monaco?.editor) {
    return;
  }

  monaco.editor.defineTheme(HG_MONACO_LIGHT, {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '6e7781' },
      { token: 'string', foreground: '0a3069' },
      { token: 'keyword', foreground: '8250df' },
    ],
    colors: {
      'editor.background': '#ffffff',
      'editor.foreground': '#333333',
      'editorCursor.foreground': '#000000',
      'editorLineNumber.foreground': '#8c959f',
      'editorLineNumber.activeForeground': '#1677ff',
      'editorLineNumber.dimmedForeground': '#b0b7c3',
      'editor.selectionBackground': '#cfe3ff',
      'editor.inactiveSelectionBackground': '#e8f0ff',
      'editor.lineHighlightBackground': '#f5f7fa',
      'editorGutter.background': '#ffffff',
      'editorIndentGuide.background': '#e6e8eb',
      'editorIndentGuide.activeBackground': '#d0d7de',
      'editorWhitespace.foreground': '#d0d7de',
      'editor.wordHighlightBorder': '#00000000',
      'editor.wordHighlightStrongBorder': '#00000000',
      'editor.wordHighlightBackground': '#00000000',
      'editor.wordHighlightStrongBackground': '#00000000',
      'editor.selectionHighlightBorder': '#00000000',
      'editor.selectionHighlightBackground': '#00000000',
      'editor.findMatchBorder': '#00000000',
      'editor.findMatchHighlightBorder': '#00000000',
      'editor.findMatchHighlightBackground': '#00000000',
      'scrollbarSlider.background': '#c1c1c1',
      'scrollbarSlider.hoverBackground': '#a8a8a8',
      'scrollbarSlider.activeBackground': '#909090',
    },
  });

  monaco.editor.defineTheme(HG_MONACO_DARK, {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '8b9cb5' },
      { token: 'string', foreground: '7dd3fc' },
      { token: 'keyword', foreground: 'c4b5fd' },
    ],
    colors: {
      'editor.background': '#0f172a',
      'editor.foreground': '#e2e8f0',
      'editorCursor.foreground': '#5eead4',
      'editorLineNumber.foreground': '#64748b',
      'editorLineNumber.activeForeground': '#5eead4',
      'editorLineNumber.dimmedForeground': '#475569',
      'editor.selectionBackground': '#1e3a5f',
      'editor.inactiveSelectionBackground': '#17263e',
      'editor.lineHighlightBackground': '#111f36',
      'editorGutter.background': '#0f172a',
      'editorIndentGuide.background': '#22324b',
      'editorIndentGuide.activeBackground': '#2d4264',
      'editorWhitespace.foreground': '#2b3e5d',
      'editor.wordHighlightBorder': '#00000000',
      'editor.wordHighlightStrongBorder': '#00000000',
      'editor.wordHighlightBackground': '#00000000',
      'editor.wordHighlightStrongBackground': '#00000000',
      'editor.selectionHighlightBorder': '#00000000',
      'editor.selectionHighlightBackground': '#00000000',
      'editor.findMatchBorder': '#00000000',
      'editor.findMatchHighlightBorder': '#00000000',
      'editor.findMatchHighlightBackground': '#00000000',
      'scrollbarSlider.background': '#334155',
      'scrollbarSlider.hoverBackground': '#475569',
      'scrollbarSlider.activeBackground': '#64748b',
    },
  });
}

export function getMonacoThemeName(isDark) {
  return isDark ? HG_MONACO_DARK : HG_MONACO_LIGHT;
}

