import type { TableDetail as TableDetailData } from '../types/result'

interface Props {
  detail: TableDetailData | null
}

export default function TableDetail({ detail }: Props) {
  if (!detail) {
    return (
      <div style={emptyStyle}>点击表节点或列表查看详情</div>
    )
  }
  const t = detail.table
  return (
    <div style={{ padding: 8, fontSize: 13 }}>
      <div style={hStyle}>{t.name}</div>
      <div style={metaStyle}>
        出现于 {t.sqlCount} 条 SQL · {t.mappers.length} 个 mapper
      </div>

      <div style={sectionTitleStyle}>字段</div>
      {t.fields.length === 0 ? (
        <div style={{ color: '#94a3b8' }}>（未知）</div>
      ) : (
        t.fields.map((f) => <div key={f}>· {f}</div>)
      )}

      <div style={sectionTitleStyle}>关联表</div>
      {detail.relatedEdges.length === 0 ? (
        <div style={{ color: '#94a3b8' }}>无</div>
      ) : (
        detail.relatedEdges.map((e, i) => (
          <div key={i} style={{ marginBottom: 4 }}>
            <b>{e.otherTable}</b>{' '}
            <span style={{ color: '#64748b' }}>
              ({e.joinType}
              {e.onFields[0]
                ? ` ${e.onFields[0].leftField}=${e.onFields[0].rightField}`
                : ''}
              {e.strength === 'WEAK' ? ' · 疑似' : ''})
            </span>
          </div>
        ))
      )}

      <div style={sectionTitleStyle}>来源 SQL</div>
      {detail.sqlSources.length === 0 ? (
        <div style={{ color: '#94a3b8' }}>无</div>
      ) : (
        detail.sqlSources.slice(0, 20).map((s, i) => (
          <div key={i} style={sqlStyle}>
            <div style={{ color: '#2563eb' }}>
              {s.mapper}.{s.statementId}
            </div>
            <pre style={preStyle}>{s.sql}</pre>
          </div>
        ))
      )}
    </div>
  )
}

const emptyStyle: React.CSSProperties = {
  padding: 16,
  color: '#94a3b8',
  fontSize: 13,
  textAlign: 'center',
}
const hStyle: React.CSSProperties = { fontSize: 16, fontWeight: 700, color: '#1e293b' }
const metaStyle: React.CSSProperties = { color: '#64748b', marginBottom: 8 }
const sectionTitleStyle: React.CSSProperties = {
  marginTop: 10,
  marginBottom: 4,
  fontWeight: 600,
  color: '#334155',
  borderBottom: '1px solid #e2e8f0',
}
const sqlStyle: React.CSSProperties = { marginBottom: 6 }
const preStyle: React.CSSProperties = {
  margin: 0,
  background: '#f8fafc',
  padding: 6,
  borderRadius: 4,
  fontSize: 11,
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
}
