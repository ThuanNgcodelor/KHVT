import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '../../catalog/catalogApi'
import type { Material } from '../../catalog/types'
export function MaterialLookup({ onPick }: { onPick: (material: Material) => void }) {
  const [text, setText] = useState(''), [q, setQ] = useState('')
  const found = useQuery({ queryKey: ['catalog', 'lookup-materials', q], queryFn: ({ signal }) => catalogApi.materials(q, signal), enabled: q.length > 0 })
  return <div className="material-lookup"><div className="form-field"><label htmlFor="po-material-lookup">Thêm từ danh mục vật tư</label><div className="header-actions"><input id="po-material-lookup" value={text} onChange={(event) => setText(event.target.value)} placeholder="Mã hoặc tên không dấu" /><button type="button" className="secondary-button" onClick={() => setQ(text.trim())} disabled={!text.trim()}>Tìm vật tư</button></div></div>
    {found.isFetching && <p role="status">Đang tìm vật tư…</p>}{found.error && <p role="alert">Chưa tải được danh mục. Thử lại hoặc nhập dòng thủ công.</p>}
    {q && found.data && !found.error && <ul className="lookup-list">{found.data.map((material) => <li key={material.id}><button type="button" onClick={() => onPick(material)}>{material.code || 'Chưa có mã'} — {material.name} ({material.defaultUnit || 'Chưa có ĐVT'})</button></li>)}{!found.data.length && <li>Không tìm thấy vật tư phù hợp.</li>}</ul>}
  </div>
}
