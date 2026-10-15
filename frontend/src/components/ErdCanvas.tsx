import { useMemo } from 'react'
import ReactFlow, {
  Background,
  Controls,
  type Edge,
  type Node,
  Position,
  BackgroundVariant,
} from 'reactflow'
import dagre from 'dagre'
import 'reactflow/dist/style.css'
import type { AnalyzeResult } from '../types/result'

const nodeWidth = 220
const nodeHeight = 140

interface TableNodeData {
  name: string
  fields: string[]
  sqlCount: number
  joinType?: string
  onLabel?: string
  [key: string]: unknown
}

/** dagre 自动布局 */
function layout(nodes: Node[], edges: Edge[]) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'LR', nodesep: 50, ranksep: 80 })
  nodes.forEach((n) => g.setNode(n.id, { width: nodeWidth, height: nodeHeight }))
  edges.forEach((e) => g.setEdge(e.source, e.target))
  dagre.layout(g)
  return nodes.map((n) => {
    const pos = g.node(n.id)
    return {
      ...n,
      position: {
        x: pos.x - nodeWidth / 2,
        y: pos.y - nodeHeight / 2,
      },
    }
  })
}

/** 自定义表卡片节点 */
function TableCard({ data }: { data: TableNodeData }) {
  return (
    <div style={cardStyle}>
      <div style={cardHeaderStyle}>{data.name}</div>
      <div style={cardBodyStyle}>
        {data.fields.length === 0 ? (
          <div style={emptyStyle}>（字段未知）</div>
        ) : (
          data.fields.slice(0, 8).map((f) => (
            <div key={f} style={fieldStyle}>· {f}</div>
          ))
        )}
        {data.fields.length > 8 && <div style={moreStyle}>+{data.fields.length - 8} …</div>}
      </div>
      <div style={cardFooterStyle}>{data.sqlCount} SQL</div>
    </div>
  )
}

const cardStyle: React.CSSProperties = {
  width: nodeWidth,
  border: '1px solid #2563eb',
  borderRadius: 8,
  background: '#fff',
  fontSize: 12,
  overflow: 'hidden',
  boxShadow: '0 2px 6px rgba(0,0,0,0.08)',
}
const cardHeaderStyle: React.CSSProperties = {
  background: '#2563eb',
  color: '#fff',
  fontWeight: 600,
  padding: '4px 8px',
}
const cardBodyStyle: React.CSSProperties = { padding: '4px 8px', maxHeight: 80, overflow: 'hidden' }
const fieldStyle: React.CSSProperties = { color: '#334155', whiteSpace: 'nowrap' }
const emptyStyle: React.CSSProperties = { color: '#94a3b8', fontStyle: 'italic' }
const moreStyle: React.CSSProperties = { color: '#94a3b8' }
const cardFooterStyle: React.CSSProperties = {
  borderTop: '1px solid #e2e8f0',
  color: '#64748b',
  padding: '2px 8px',
  textAlign: 'right',
}

const nodeTypes = { table: TableCard }

interface Props {
  result: AnalyzeResult
  onSelectTable: (name: string) => void
  selectedTable?: string
}

export default function ErdCanvas({ result, onSelectTable, selectedTable }: Props) {
  const { nodes, edges } = useMemo(() => {
    const n: Node<TableNodeData>[] = result.tables.map((t) => ({
      id: t.name,
      type: 'table',
      position: { x: 0, y: 0 },
      data: { name: t.name, fields: t.fields, sqlCount: t.sqlCount },
      targetPosition: Position.Left,
      sourcePosition: Position.Right,
    }))
    const e: Edge[] = result.edges.map((edge, i) => {
      const f0 = edge.onFields[0]
      const label = `${edge.joinType}${f0 ? ` ${f0.leftField}=${f0.rightField}` : ''}`
      return {
        id: `e-${i}`,
        source: edge.left,
        target: edge.right,
        label,
        labelStyle: { fontSize: 11 },
        style: { stroke: edge.strength === 'WEAK' ? '#f59e0b' : '#64748b' },
        animated: edge.joinType !== 'INNER',
      }
    })
    const laid = layout(n, e)
    return { nodes: laid, edges: e }
  }, [result])

  return (
    <div style={{ width: '100%', height: '100%' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        fitView
        onNodeClick={(_, node) => onSelectTable(node.id)}
        defaultEdgeOptions={{ type: 'smoothstep' }}
      >
        <Background variant={BackgroundVariant.Dots} gap={16} />
        <Controls />
      </ReactFlow>
    </div>
  )
}
