import { useState } from 'react'
import {
  Box, Button, Card, CardContent, Typography, Alert, CircularProgress,
  FormControl, InputLabel, Select, MenuItem, Stack, Divider, LinearProgress, Chip,
} from '@mui/material'
import { generateBatch, BatchJobResponse, FileType, Library } from '../api/client'

const FILE_TYPES: FileType[] = ['CODA', 'SWIFT']
const LIBRARIES: Library[] = ['BEANIO', 'FIXFORMAT4J', 'FIXEDLENGTH', 'BINDY', 'CAMEL_BEANIO', 'VELOCITY', 'SPRING_BATCH']

interface BatchRunSummary {
  fileType: FileType
  library: Library
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  jobExecutionId?: number
  fileName?: string
  durationMs?: number
  error?: string
}

export default function BatchRunnerView() {
  const [fileType, setFileType] = useState<FileType>('CODA')
  const [library, setLibrary] = useState<Library>('BEANIO')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<BatchJobResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // "Run all" state
  const [runAllBusy, setRunAllBusy] = useState(false)
  const [runAllSummary, setRunAllSummary] = useState<BatchRunSummary[]>([])

  const handleSubmit = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await generateBatch({ fileType, library })
      setResult(data)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Batch job failed')
    } finally {
      setLoading(false)
    }
  }

  const handleRunAll = async () => {
    setRunAllBusy(true)
    setError(null)
    setResult(null)
    const combos: BatchRunSummary[] = FILE_TYPES.flatMap(ft =>
      LIBRARIES.map(lib => ({ fileType: ft, library: lib, status: 'PENDING' as const }))
    )
    setRunAllSummary(combos)

    for (let i = 0; i < combos.length; i++) {
      setRunAllSummary(prev => prev.map((c, idx) => idx === i ? { ...c, status: 'RUNNING' } : c))
      const start = Date.now()
      try {
        const data = await generateBatch({ fileType: combos[i].fileType, library: combos[i].library })
        setRunAllSummary(prev => prev.map((c, idx) => idx === i ? {
          ...c,
          status: data.status === 'COMPLETED' ? 'COMPLETED' : 'FAILED',
          jobExecutionId: data.jobExecutionId,
          fileName: data.fileName,
          durationMs: Date.now() - start,
        } : c))
      } catch (e: unknown) {
        setRunAllSummary(prev => prev.map((c, idx) => idx === i ? {
          ...c,
          status: 'FAILED',
          error: e instanceof Error ? e.message : 'failed',
          durationMs: Date.now() - start,
        } : c))
      }
    }
    setRunAllBusy(false)
  }

  const runAllProgress = runAllSummary.length === 0 ? 0
    : (100 * runAllSummary.filter(c => c.status === 'COMPLETED' || c.status === 'FAILED').length) / runAllSummary.length

  const statusColor = (s: BatchRunSummary['status']) =>
    s === 'COMPLETED' ? 'success' : s === 'FAILED' ? 'error' : s === 'RUNNING' ? 'info' : 'default'

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Batch Runner</Typography>
      <Typography variant="body1" color="text.secondary" mb={3}>
        Trigger a Spring Batch job to generate a banking file using the selected library, or run every
        FileType × Library combination at once.
      </Typography>

      <Card sx={{ maxWidth: 560, mb: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <FormControl fullWidth>
              <InputLabel>File Type</InputLabel>
              <Select value={fileType} label="File Type" onChange={e => setFileType(e.target.value as FileType)}
                disabled={runAllBusy}>
                {FILE_TYPES.map(ft => <MenuItem key={ft} value={ft}>{ft}</MenuItem>)}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel>Formatter Library</InputLabel>
              <Select value={library} label="Formatter Library" onChange={e => setLibrary(e.target.value as Library)}
                disabled={runAllBusy}>
                {LIBRARIES.map(lib => <MenuItem key={lib} value={lib}>{lib}</MenuItem>)}
              </Select>
            </FormControl>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <Button variant="contained" size="large" onClick={handleSubmit} disabled={loading || runAllBusy}
                startIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}>
                {loading ? 'Running…' : 'Generate File'}
              </Button>
              <Button variant="outlined" size="large" color="secondary" onClick={handleRunAll}
                disabled={loading || runAllBusy}
                startIcon={runAllBusy ? <CircularProgress size={18} color="inherit" /> : undefined}>
                {runAllBusy ? 'Running all…' : `Run All Combinations (${FILE_TYPES.length * LIBRARIES.length})`}
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {runAllSummary.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Run All Combinations — {runAllSummary.filter(c => c.status === 'COMPLETED').length}/{runAllSummary.length} completed
            </Typography>
            <LinearProgress variant="determinate" value={runAllProgress} sx={{ mb: 2 }} />
            <Stack spacing={1}>
              {runAllSummary.map((c, i) => (
                <Stack key={i} direction="row" spacing={1} alignItems="center"
                  sx={{ fontFamily: 'monospace', fontSize: 13 }}>
                  <Chip size="small" label={c.status} color={statusColor(c.status)} sx={{ minWidth: 96 }} />
                  <Box sx={{ minWidth: 64 }}>{c.fileType}</Box>
                  <Box sx={{ minWidth: 140 }}>{c.library}</Box>
                  {c.durationMs !== undefined && <Box sx={{ minWidth: 80, color: 'text.secondary' }}>{c.durationMs} ms</Box>}
                  {c.fileName && <Box sx={{ color: 'text.secondary', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.fileName}</Box>}
                  {c.error && <Box sx={{ color: 'error.main' }}>{c.error}</Box>}
                </Stack>
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      {result && (
        <Card>
          <CardContent>
            <Stack spacing={1} mb={2}>
              <Typography><b>Job ID:</b> {result.jobExecutionId}</Typography>
              <Typography><b>Status:</b> {result.status}</Typography>
              <Typography><b>File:</b> {result.fileName}</Typography>
              <Typography><b>Timestamp:</b> {new Date(result.timestamp).toLocaleString()}</Typography>
            </Stack>
            {result.fileContent && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="subtitle2" gutterBottom>File Preview</Typography>
                <Box component="pre" sx={{
                  fontFamily: 'monospace', fontSize: 11, overflow: 'auto',
                  maxHeight: 400, bgcolor: 'action.hover', p: 1, borderRadius: 1,
                }}>
                  {result.fileContent.slice(0, 4000)}{result.fileContent.length > 4000 ? '\n…(truncated)' : ''}
                </Box>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </Box>
  )
}
