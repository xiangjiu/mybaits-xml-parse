import type {
  AnalyzeResult,
  ProgressResponse,
  TableDetail,
} from '../types/result'

const BASE = '/api'

export async function analyze(baseDir: string): Promise<{ analyzeId: string }> {
  const res = await fetch(`${BASE}/analyze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ baseDir }),
  })
  if (!res.ok) throw new Error(`analyze failed: ${res.status}`)
  return res.json()
}

export async function getProgress(id: string): Promise<ProgressResponse> {
  const res = await fetch(`${BASE}/progress/${id}`)
  if (!res.ok) throw new Error(`progress failed: ${res.status}`)
  return res.json()
}

export async function getResult(id: string): Promise<AnalyzeResult> {
  const res = await fetch(`${BASE}/result/${id}`)
  if (!res.ok) throw new Error(`result failed: ${res.status}`)
  return res.json()
}

export async function getTableDetail(
  id: string,
  tableName: string,
): Promise<TableDetail> {
  const res = await fetch(`${BASE}/table/${id}/${encodeURIComponent(tableName)}`)
  if (!res.ok) throw new Error(`table failed: ${res.status}`)
  return res.json()
}

export async function exportMermaid(id: string): Promise<string> {
  const res = await fetch(`${BASE}/export/${id}?format=mermaid`)
  if (!res.ok) throw new Error(`export failed: ${res.status}`)
  return res.text()
}
