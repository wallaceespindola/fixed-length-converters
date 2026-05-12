import { useQuery } from '@tanstack/react-query'
import {
  Box, Typography, CircularProgress, Alert, Button, Stack, Grid, Card, CardContent,
} from '@mui/material'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  LineChart, Line,
} from 'recharts'
import { getBenchmarkResults, BenchmarkResultResponse } from '../api/client'

const LIBRARY_COLORS: Record<string, string> = {
  BEANIO: '#1976d2',
  FIXFORMAT4J: '#2e7d32',
  FIXEDLENGTH: '#ed6c02',
  BINDY: '#9c27b0',
}

function byLibrary(data: BenchmarkResultResponse[]) {
  const map: Record<string, { throughput: number; genDuration: number; batchDuration: number; count: number }> = {}
  data.forEach(d => {
    if (!map[d.library]) map[d.library] = { throughput: 0, genDuration: 0, batchDuration: 0, count: 0 }
    map[d.library].throughput += d.throughputRps
    map[d.library].genDuration += d.generationDurationMs
    map[d.library].batchDuration += d.batchDurationMs
    map[d.library].count++
  })
  return Object.entries(map).map(([lib, v]) => ({
    library: lib,
    avgThroughput: v.count ? +(v.throughput / v.count).toFixed(2) : 0,
    avgGenDuration: v.count ? +(v.genDuration / v.count).toFixed(0) : 0,
    avgBatchDuration: v.count ? +(v.batchDuration / v.count).toFixed(0) : 0,
  }))
}

export default function BenchmarkDashboardView() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['benchmarkResults'],
    queryFn: getBenchmarkResults,
    refetchInterval: 30_000,
  })

  if (isLoading) return <CircularProgress sx={{ mt: 4 }} />
  if (error) return <Alert severity="error">Failed to load benchmark results</Alert>

  const results = data ?? []
  const libraryData = byLibrary(results)
  const timelineData = results.slice(0, 20).reverse().map((r, i) => ({
    run: i + 1,
    throughput: r.throughputRps,
    batchDuration: r.batchDurationMs,
    genDuration: r.generationDurationMs,
    library: r.library,
  }))

  const exportFile = (format: 'csv' | 'markdown' | 'json') => {
    window.open(`/api/benchmark/export/${format}`, '_blank')
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Benchmark Dashboard</Typography>

      <Stack direction="row" spacing={1} mb={3}>
        <Button variant="outlined" size="small" onClick={() => exportFile('csv')}>Export CSV</Button>
        <Button variant="outlined" size="small" onClick={() => exportFile('json')}>Export JSON</Button>
        <Button variant="outlined" size="small" onClick={() => exportFile('markdown')}>Export Markdown</Button>
      </Stack>

      {results.length === 0 ? (
        <Alert severity="info">
          No benchmark data yet. Run some batch jobs to populate metrics.
        </Alert>
      ) : (
        <Grid container spacing={3}>
          {/* Row 1: throughput bar + duration bar */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>Avg Throughput by Library (ops/s)</Typography>
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={libraryData} margin={{ top: 8, right: 16, bottom: 36, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="library" interval={0} angle={-30} textAnchor="end" height={60}
                      tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="avgThroughput" name="Avg Throughput (ops/s)"
                      fill="#1976d2"
                      label={{ position: 'top', fontSize: 10 }} />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>Avg Duration by Library (ms)</Typography>
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={libraryData} margin={{ top: 8, right: 16, bottom: 36, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="library" interval={0} angle={-30} textAnchor="end" height={60}
                      tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Bar dataKey="avgBatchDuration" name="Avg Batch Duration (ms)"
                      fill="#ed6c02"
                      label={{ position: 'top', fontSize: 10 }} />
                    <Bar dataKey="avgGenDuration" name="Avg Generation Duration (ms)"
                      fill="#9c27b0"
                      label={{ position: 'top', fontSize: 10 }} />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Grid>

          {/* Row 2: throughput over runs + duration over runs */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>Throughput Over Runs</Typography>
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart data={timelineData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="run" label={{ value: 'Run #', position: 'insideBottom', offset: -2 }} />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="throughput" name="Throughput (ops/s)"
                      stroke={LIBRARY_COLORS['BEANIO']} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>Avg Duration Over Runs (ms)</Typography>
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart data={timelineData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="run" label={{ value: 'Run #', position: 'insideBottom', offset: -2 }} />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="batchDuration" name="Batch Duration (ms)"
                      stroke={LIBRARY_COLORS['FIXFORMAT4J']} dot={false} />
                    <Line type="monotone" dataKey="genDuration" name="Generation Duration (ms)"
                      stroke={LIBRARY_COLORS['BINDY']} dot={false} strokeDasharray="4 2" />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </Grid>

          {/* Row 3: summary */}
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>Library Summary</Typography>
                <Stack direction="row" spacing={4} flexWrap="wrap">
                  {libraryData.map(d => (
                    <Box key={d.library}>
                      <Typography variant="subtitle2" sx={{ color: LIBRARY_COLORS[d.library] }}>
                        {d.library}
                      </Typography>
                      <Typography variant="body2">Avg throughput: <b>{d.avgThroughput} ops/s</b></Typography>
                      <Typography variant="body2">Avg batch duration: <b>{d.avgBatchDuration} ms</b></Typography>
                      <Typography variant="body2">Avg gen duration: <b>{d.avgGenDuration} ms</b></Typography>
                    </Box>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </Box>
  )
}
