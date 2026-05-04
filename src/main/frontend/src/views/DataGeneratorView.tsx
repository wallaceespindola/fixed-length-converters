import { useState } from 'react'
import { Box, Button, Card, CardContent, Typography, Alert, CircularProgress, Stack } from '@mui/material'
import { generateDomain, GenerateDomainResponse } from '../api/client'

export default function DataGeneratorView() {
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<GenerateDomainResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleGenerate = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await generateDomain()
      setResult(data)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Generation failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Generate Banking Data</Typography>
      <Typography variant="body1" color="text.secondary" mb={3}>
        Seeds the H2 database with 20 accounts, 200 transactions, and 10 statements.
      </Typography>

      <Button variant="contained" size="large" onClick={handleGenerate} disabled={loading}
        startIcon={loading ? <CircularProgress size={18} color="inherit" /> : undefined}>
        {loading ? 'Generating…' : 'Generate Sample Banking Data'}
      </Button>

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
