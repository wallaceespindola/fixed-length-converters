import { useState } from 'react'
import { Box, Button, Card, CardContent, Typography, Alert, CircularProgress, Stack } from '@mui/material'
import { generateDomain, GenerateDomainResponse, LoadProfile } from '../api/client'

export default function DataGeneratorView() {
  const [loadingProfile, setLoadingProfile] = useState<LoadProfile | null>(null)
  const [result, setResult] = useState<GenerateDomainResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleGenerate = async (profile: LoadProfile) => {
    setLoadingProfile(profile)
    setError(null)
    try {
      const data = await generateDomain(profile)
      setResult(data)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Generation failed')
    } finally {
      setLoadingProfile(null)
    }
  }

  const loading = loadingProfile !== null

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Generate Banking Data</Typography>
      <Typography variant="body1" color="text.secondary" mb={3}>
        Seeds the H2 database with sample accounts, transactions, and statements. Pick a load profile:
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <Button variant="contained" size="large"
          onClick={() => handleGenerate('LOW')}
          disabled={loading}
          startIcon={loadingProfile === 'LOW' ? <CircularProgress size={18} color="inherit" /> : undefined}>
          {loadingProfile === 'LOW' ? 'Generating…' : 'Low Load (20 accounts, 200 txns)'}
        </Button>
        <Button variant="outlined" size="large" color="primary"
          onClick={() => handleGenerate('HIGH')}
          disabled={loading}
          startIcon={loadingProfile === 'HIGH' ? <CircularProgress size={18} color="inherit" /> : undefined}>
          {loadingProfile === 'HIGH' ? 'Generating…' : 'High Load (200 accounts, 2 000 txns)'}
        </Button>
      </Stack>

      <Typography variant="caption" color="text.secondary">
        Low: 20 accounts, 200 transactions, 10 statements. High: 200 accounts, 2 000 transactions, 100 statements.
      </Typography>

      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

      {result && (
        <Card sx={{ mt: 3, maxWidth: 480 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>Generation Result</Typography>
            <Stack spacing={1}>
              <Typography><b>Operation ID:</b> {result.operationId}</Typography>
              <Typography><b>Accounts generated:</b> {result.accountsGenerated}</Typography>
              <Typography><b>Transactions generated:</b> {result.transactionsGenerated}</Typography>
              <Typography><b>Timestamp:</b> {new Date(result.timestamp).toLocaleString()}</Typography>
            </Stack>
          </CardContent>
        </Card>
      )}
    </Box>
  )
}
