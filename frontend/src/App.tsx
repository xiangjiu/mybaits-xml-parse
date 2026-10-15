import { useEffect, useRef, useState } from 'react'
import ErdCanvas from './components/ErdCanvas'
import TableList from './components/TableList'
import TableDetail from './components/TableDetail'
import type { AnalyzeResult, ProgressResponse, TableDetail as TableDetailData } from './types/result'
import { analyze, getProgress, getResult, getTableDetail, exportMermaid } from './api/client'

export default function App() {
  const [baseDir, setBaseDir] = useState('')
  const [analyzeId, setAnalyzeId] = useState<string | null>(null)
  const [progress, setProgress] = useState<ProgressResponse | null>(null)
  const [result, setResult] = useState<AnalyzeResult | null>(null)
  const [selectedTable, setSelectedTable] = useState<string | undefined>()
  const [detail, setDetail] = useState<TableDetailData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const startAnalyze = async () => {
    setError(null)
    setResult(null)
    setDetail(null)
    setSelectedTable(undefined)
    try {
      const { analyzeId: id } = await analyze(baseDir)
      setAnalyzeId(id)
      pollProgress(id)
    } catch (e) {
      setError(String(e))
    }
  }

  const pollProgress = (id: string) => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(async () => {
      try {
        const p = await getProgress(id)
        setProgress(p)
        if (p.status === 'DONE') {
          if (timerRef.current) clearInterval(timerRef.current)
          const r = await getResult(id)
          setResult(r)
        } else if (p.status === 'FAILED') {
          if (timerRef.current) clearInterval(timerRef.current)
          setError(p.message || '分析失败')
        }
      } catch {
        /* keep polling */
      }
    }, 1000)
  }

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [])

  const onSelectTable = async (name: string) => {
    setSelectedTable(name)
    if (!analyzeId) return
    try {
      const d = await getTableDetail(analyzeId, name)
      setDetail(d)
    } catch (e) {
      setError(String(e))
    }
  }

  const onExport = async () => {
    if (!analyzeId) return
    try {
      const text = await exportMermaid(analyzeId)
      const blob = new Blob([text], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'er.mmd'
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(String(e))
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <header style={headerStyle}>
        <span style={{ fontWeight: 700, fontSize: 15 }}>MyBatis 表关系分析</span>
        <input
          placeholder="mapper.xml 所在目录，如 D:/projects/foo/src/main/resources/mapper"
          value={baseDir}
          onChange={(e) => setBaseDir(e.target.value)}
          style={dirInputStyle}
        />
        <button onClick={startAnalyze} disabled={!baseDir} style={btnStyle(!baseDir)}>
          分析
        </button>
        <button onClick={onExport} disabled={!result} style={btnStyle(!result)}>
          导出 Mermaid
        </button>
        {progress && (
          <span style={{ fontSize: 12, color: '#64748b' }}>
            {progress.status === 'DONE'
              ? `完成 ${progress.total}/${progress.total}`
              : `${progress.status} ${progress.processed}/${progress.total}${
                  progress.currentFile ? ' · ' + progress.currentFile : ''
                }`}
          </span>
        )}
        {result && (
          <span style={{ fontSize: 12, color: '#475569' }}>
            {result.summary.tableCount} 表 · {result.summary.edgeCount} 关联 ·{' '}
            {result.summary.mapperCount} mapper
            {result.summary.parseFailCount > 0
              ? ` · ${result.summary.parseFailCount} 条解析降级`
              : ''}
          </span>
        )}
      </header>

      {error && (
        <div style={errorBarStyle}>{error}</div>
      )}

      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <aside style={sidebarStyle}>
          {result && (
            <TableList
              tables={result.tables}
              selectedTable={selectedTable}
              onSelect={onSelectTable}
            />
          )}
        </aside>
        <main style={{ flex: 1, minWidth: 0 }}>
          {result ? (
            <ErdCanvas
              result={result}
              onSelectTable={onSelectTable}
              selectedTable={selectedTable}
            />
          ) : (
            <div style={placeholderStyle}>输入目录后点击「分析」</div>
          )}
        </main>
        <aside style={detailStyle}>
          <TableDetail detail={detail} />
        </aside>
      </div>
    </div>
  )
}

const headerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '8px 12px',
  background: '#f8fafc',
  borderBottom: '1px solid #e2e8f0',
}
const dirInputStyle: React.CSSProperties = {
  flex: 1,
  maxWidth: 520,
  padding: '6px 8px',
  border: '1px solid #cbd5e1',
  borderRadius: 6,
  fontSize: 13,
}
const btnStyle = (disabled: boolean): React.CSSProperties => ({
  padding: '6px 12px',
  border: 'none',
  borderRadius: 6,
  background: disabled ? '#cbd5e1' : '#2563eb',
  color: '#fff',
  cursor: disabled ? 'not-allowed' : 'pointer',
  fontSize: 13,
})
const errorBarStyle: React.CSSProperties = {
  background: '#fee2e2',
  color: '#b91c1c',
  padding: '6px 12px',
  fontSize: 13,
}
const sidebarStyle: React.CSSProperties = {
  width: 220,
  borderRight: '1px solid #e2e8f0',
  overflow: 'hidden',
  display: 'flex',
  flexDirection: 'column',
}
const detailStyle: React.CSSProperties = {
  width: 320,
  borderLeft: '1px solid #e2e8f0',
  overflowY: 'auto',
}
const placeholderStyle: React.CSSProperties = {
  height: '100%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#94a3b8',
}
