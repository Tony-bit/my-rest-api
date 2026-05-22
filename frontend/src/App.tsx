import { BrowserRouter, Routes, Route } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import Dashboard from '@/pages/Dashboard'
import PlanList from '@/pages/PlanList'
import PlanCreate from '@/pages/PlanCreate'
import PlanDetail from '@/pages/PlanDetail'
import PlanEdit from '@/pages/PlanEdit'
import TradeList from '@/pages/TradeList'
import TradeCreate from '@/pages/TradeCreate'
import TradeEdit from '@/pages/TradeEdit'
import SnapshotList from '@/pages/SnapshotList'
import Settings from '@/pages/Settings'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="plans" element={<PlanList />} />
          <Route path="plans/new" element={<PlanCreate />} />
          <Route path="plans/sell/new" element={<PlanCreate />} />
          <Route path="plans/:id" element={<PlanDetail />} />
          <Route path="plans/:id/edit" element={<PlanEdit />} />
          <Route path="actual-trades" element={<TradeList />} />
          <Route path="actual-trades/new" element={<TradeCreate />} />
          <Route path="actual-trades/:id/edit" element={<TradeEdit />} />
          <Route path="snapshots" element={<SnapshotList />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
