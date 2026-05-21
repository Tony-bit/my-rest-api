import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/', label: '对比分析' },
  { to: '/plans', label: '预案管理' },
  { to: '/actual-trades', label: '实盘记录' },
  { to: '/snapshots', label: '历史快照' },
  { to: '/settings', label: '系统设置' },
]

export default function AppLayout() {
  return (
    <div className="flex min-h-screen bg-gray-950 text-gray-100 font-sans">
      <aside className="w-52 flex-shrink-0 border-r border-gray-800 flex flex-col">
        <div className="h-14 flex items-center px-4 border-b border-gray-800">
          <h1 className="text-sm font-medium text-gray-200">预案与实盘对比</h1>
        </div>
        <nav className="flex-1 py-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center h-10 px-4 text-sm transition-colors ${
                  isActive
                    ? 'text-blue-400 bg-blue-950 border-r-2 border-blue-400'
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-900'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 flex flex-col min-w-0">
        <Outlet />
      </main>
    </div>
  )
}
