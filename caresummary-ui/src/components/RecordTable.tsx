import { useState } from 'react'
import type { ClinicalItem } from '../types'

interface Props {
  title: string
  items: ClinicalItem[]
  highlight: string | null
  detailLabel: string
  /** Show an "Active only" switch (for conditions and medicines) */
  activeFilter?: boolean
}

/** One table of records. Each row has an id like "src-Condition/2266" so the AI panel can jump to it. */
export default function RecordTable({ title, items, highlight, detailLabel, activeFilter = false }: Props) {
  const [activeOnly, setActiveOnly] = useState(activeFilter)

  const isActive = (i: ClinicalItem) => (i.detail ?? '').startsWith('active')
  // A highlighted (AI-cited) row is always shown, even if it's not active
  const shown = activeOnly ? items.filter((i) => isActive(i) || i.source === highlight) : items
  const activeCount = items.filter(isActive).length

  return (
    <div className="records">
      <div className="records-head">
        <h3>
          {title}{' '}
          <span className="muted">
            ({activeFilter ? `${activeCount} active, ${items.length} total` : items.length})
          </span>
        </h3>
        {activeFilter && items.length > 0 && (
          <label className="toggle">
            <input type="checkbox" checked={activeOnly} onChange={(e) => setActiveOnly(e.target.checked)} />
            Active only
          </label>
        )}
      </div>
      {shown.length === 0 ? (
        <div className="muted small">{items.length === 0 ? 'None recorded.' : 'None active.'}</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>{detailLabel}</th>
                <th>Date</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((i) => (
                <tr key={i.source} id={`src-${i.source}`} className={i.source === highlight ? 'hl' : undefined}>
                  <td>{i.name}</td>
                  <td>{i.detail ?? '—'}</td>
                  <td className="nowrap">{i.date ?? '—'}</td>
                  <td className="src">{i.source}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
