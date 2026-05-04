import { useState } from 'react'
import {
  Box, Button, Card, CardContent, Typography, Alert, CircularProgress,
  FormControl, InputLabel, Select, MenuItem, Stack, Divider,
} from '@mui/material'
import { generateBatch, BatchJobResponse, FileType, Library } from '../api/client'

const FILE_TYPES: FileType[] = ['CODA', 'SWIFT']
const LIBRARIES: Library[] = ['BEANIO', 'FIXFORMAT4J', 'FIXEDLENGTH', 'BINDY']

export default function BatchRunnerView() {
  const [fileType, setFileType] = useState<FileType>('CODA')
  const [library, setLibrary] = useState<Library>('BEANIO')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<BatchJobResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Batch Runner</Typography>
      <Typography variant="body1" color="text.secondary" mb={3}>
        Trigger a Spring Batch job to generate a banking file using the selected library.
      </Typography>

      <Card sx={{ maxWidth: 480, mb: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <FormControl fullWidth>
              <InputLabel>File Type</InputLabel>
              <Select value={fileType} label="File Type" onChange={e => setFileType(e.target.value as FileType)}>
                {FILE_TYPES.map(ft => <MenuItem key={ft} value={ft}>{ft}</MenuItem>)}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel>Formatter Library</InputLabel>
              <Select value={library} label="Formatter Library" onChange={e => setLibrary(e.target.value as Library)}>
                {LIBRARIES.map(lib => <MenuItem key={lib} value={lib}>{lib}</MenuItem>)}
              </Select>
            </FormControl>

            <Button variant="contained" size="large" onClick={handleSubmit} disabled={loading}
              startIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}>
              {loading ? 'Running…' : 'Generate File'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

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
