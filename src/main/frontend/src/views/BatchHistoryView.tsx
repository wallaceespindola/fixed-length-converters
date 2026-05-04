import { useQuery } from '@tanstack/react-query'
import {
  Box, Typography, CircularProgress, Alert, Chip,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper,
} from '@mui/material'
import { getBatchHistory } from '../api/client'

const statusColor = (s: string) =>
  s === 'COMPLETED' ? 'success' : s === 'FAILED' ? 'error' : 'default'

export default function BatchHistoryView() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['batchHistory'],
    queryFn: getBatchHistory,
    refetchInterval: 15_000,
  })

  if (isLoading) return <CircularProgress sx={{ mt: 4 }} />
  if (error) return <Alert severity="error">Failed to load batch history</Alert>

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Batch History</Typography>
      <Typography variant="body1" color="text.secondary" mb={2}>
        Last 50 Spring Batch job executions.
      </Typography>

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell><b>Job ID</b></TableCell>
              <TableCell><b>File Type</b></TableCell>
              <TableCell><b>Library</b></TableCell>
              <TableCell><b>Status</b></TableCell>
              <TableCell><b>Duration (ms)</b></TableCell>
              <TableCell><b>Started</b></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(data ?? []).length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ color: 'text.secondary' }}>
                  No executions yet — run a batch job first.
                </TableCell>
              </TableRow>
            ) : (data ?? []).map(row => (
              <TableRow key={row.jobExecutionId} hover>
                <TableCell>{row.jobExecutionId}</TableCell>
                <TableCell>{row.fileType}</TableCell>
                <TableCell>{row.library}</TableCell>
                <TableCell>
                  <Chip label={row.status} color={statusColor(row.status) as 'success' | 'error' | 'default'} size="small" />
                </TableCell>
                <TableCell>{row.durationMs}</TableCell>
                <TableCell>{row.startTime ? new Date(row.startTime).toLocaleString() : '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
