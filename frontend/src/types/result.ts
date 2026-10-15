export interface TableNode {
  name: string
  mappers: string[]
  sqlCount: number
  fields: string[]
  fullNames: string[]
}

export interface OnField {
  leftField: string
  rightField: string
}

export interface SqlSource {
  mapper: string
  statementId: string
  sql: string
}

export interface TableEdge {
  left: string
  right: string
  joinType: string
  onFields: OnField[]
  sources: SqlSource[]
  strength: string
}

export interface Summary {
  tableCount: number
  edgeCount: number
  mapperCount: number
  statementCount: number
  parseFailCount: number
}

export interface AnalyzeResult {
  tables: TableNode[]
  edges: TableEdge[]
  summary: Summary
}

export interface ProgressResponse {
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'
  processed: number
  total: number
  currentFile: string
  message?: string
}

export interface TableDetail {
  table: TableNode
  relatedEdges: Array<{
    otherTable: string
    joinType: string
    onFields: OnField[]
    strength: string
  }>
  sqlSources: SqlSource[]
}
