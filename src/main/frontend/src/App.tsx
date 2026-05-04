import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import {
  Box, CssBaseline, Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  AppBar, Toolbar, Typography, IconButton, Tooltip, createTheme, ThemeProvider,
  Link, Divider,
} from '@mui/material'
import { getInfo } from './api/client'
import DashboardIcon from '@mui/icons-material/Dashboard'
import StorageIcon from '@mui/icons-material/Storage'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import HistoryIcon from '@mui/icons-material/History'
import BarChartIcon from '@mui/icons-material/BarChart'
import Brightness4Icon from '@mui/icons-material/Brightness4'
import Brightness7Icon from '@mui/icons-material/Brightness7'
import DashboardView from './views/DashboardView'
import DataGeneratorView from './views/DataGeneratorView'
import BatchRunnerView from './views/BatchRunnerView'
import BatchHistoryView from './views/BatchHistoryView'
import BenchmarkDashboardView from './views/BenchmarkDashboardView'

const DRAWER_WIDTH = 220

const NAV = [
  { label: 'Dashboard',  path: '/',          icon: <DashboardIcon /> },
  { label: 'Generate Data', path: '/generate', icon: <StorageIcon /> },
  { label: 'Batch Runner', path: '/batch',    icon: <PlayArrowIcon /> },
  { label: 'History',    path: '/history',   icon: <HistoryIcon /> },
  { label: 'Benchmark',  path: '/benchmark', icon: <BarChartIcon /> },
]

export default function App() {
  const stored = localStorage.getItem('colorMode') as 'light' | 'dark' | null
  const [mode, setMode] = useState<'light' | 'dark'>(stored ?? 'light')
  const { data: info } = useQuery({ queryKey: ['info'], queryFn: getInfo, staleTime: Infinity })
  const appVersion = (info as any)?.app?.version ?? ''

  const theme = useMemo(() =>
    createTheme({ palette: { mode } }),
    [mode],
  )

  const toggleMode = () => {
    const next = mode === 'light' ? 'dark' : 'light'
    setMode(next)
    localStorage.setItem('colorMode', next)
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Box sx={{ display: 'flex' }}>
          <AppBar position="fixed" sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
            <Toolbar>
              <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
                Banking Fixed-Length File Platform
              </Typography>
              <Tooltip title={`Switch to ${mode === 'light' ? 'dark' : 'light'} mode`}>
                <IconButton color="inherit" onClick={toggleMode}>
                  {mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon />}
                </IconButton>
              </Tooltip>
            </Toolbar>
          </AppBar>

          <Drawer variant="permanent" sx={{
            width: DRAWER_WIDTH,
            '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
          }}>
            <Toolbar />
            <List dense>
              {NAV.map(({ label, path, icon }) => (
                <ListItemButton key={path} component={NavLink} to={path}
                  sx={{ '&.active': { bgcolor: 'action.selected' } }}>
                  <ListItemIcon>{icon}</ListItemIcon>
                  <ListItemText primary={label} />
                </ListItemButton>
              ))}
            </List>
          </Drawer>

          <Box component="main" sx={{ flexGrow: 1, p: 3, mt: 8, display: 'flex', flexDirection: 'column', minHeight: 'calc(100vh - 64px)' }}>
            <Box sx={{ flexGrow: 1 }}>
              <Routes>
                <Route path="/"          element={<DashboardView />} />
                <Route path="/generate"  element={<DataGeneratorView />} />
                <Route path="/batch"     element={<BatchRunnerView />} />
                <Route path="/history"   element={<BatchHistoryView />} />
                <Route path="/benchmark" element={<BenchmarkDashboardView />} />
              </Routes>
            </Box>

            <Box component="footer" sx={{ mt: 6 }}>
              <Divider />
              <Box sx={{
                py: 2, px: 1, display: 'flex', alignItems: 'center',
                justifyContent: 'space-between', flexWrap: 'wrap', gap: 1,
              }}>
                <Typography variant="caption" color="text.secondary">
                  Banking Fixed-Length File Platform{appVersion && <>&nbsp;·&nbsp; v{appVersion}</>}
                </Typography>
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    By{' '}
                    <Link href="https://www.linkedin.com/in/wallaceespindola/"
                      target="_blank" rel="noopener" underline="hover">
                      Wallace Espindola
                    </Link>
                  </Typography>
                  <Link href="https://github.com/wallaceespindola/fixed-length-converters"
                    target="_blank" rel="noopener" variant="caption" underline="hover">
                    GitHub
                  </Link>
                  <Link href="https://www.linkedin.com/in/wallaceespindola/"
                    target="_blank" rel="noopener" variant="caption" underline="hover">
                    LinkedIn
                  </Link>
                </Box>
              </Box>
            </Box>
          </Box>
        </Box>
      </BrowserRouter>
    </ThemeProvider>
  )
}
