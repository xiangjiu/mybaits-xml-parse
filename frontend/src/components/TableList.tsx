import { useState } from 'react'
import type { TableNode } from '../types/result'

interface Props {
  tables: TableNode[]
  selectedTable?: string
  onSelect: (name: string) => void
}

export default function TableList({ tables, selectedTable, onSelect }: Props) {
  const [kw, setKw] = useState('')
  const filtered = tables.filter((t) =>
    t.name.toLowerCase().includes(kw.toLowerCase()),
  )

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <input
        placeholder="搜索表名…"
        value={kw}
        onChange={(e) => setKw(e.target.value)}
        style={inputStyle}
      />
      <div style={{ flex: 1, overflowY: 'auto', marginTop: 8 }}>
        {filtered.map((t) => (
          <div
            key={t.name}
            onClick={() => onSelect(t.name)}
            style={{
              ...itemStyle,
              background: t.name === selectedTable ? '#dbeafe' : '#fff',
            }}
          >
            <div style={{ fontWeight: 600, color: '#1e293b' }}>{t.name}</div>
            <div style={{ fontSize: 11, color: '#64748b' }}>
              {t.sqlCount} SQL · {t.mappers.length} mapper
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '6px 8px',
  border: '1px solid #cbd5e1',
  borderRadius: 6,
  fontSize: 13,
}
const itemStyle: React.CSSProperties = {
  padding: '6px 8px',
  borderBottom: '1px solid #f1f5f9',
  cursor: 'pointer',
  fontSize: 13,
}
