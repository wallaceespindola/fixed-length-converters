import { useQuery } from '@tanstack/react-query'
import { Box, Card, CardContent, Typography, Button, Chip, Stack, Grid } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { getHealth, getInfo } from '../api/client'

export default function DashboardView() {
  const navigate = useNavigate()
  const { data: health } = useQuery({ queryKey: ['health'], queryFn: getHealth, refetchInterval: 30_000 })
  const { data: info } = useQuery({ queryKey: ['info'], queryFn: getInfo })

  const status = (health as { status?: string })?.status ?? 'UNKNOWN'

  return (
    <Box>
      <Typography variant="h4" gutterBottom fontWeight={700}>Dashboard</Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Health Status</Typography>
              <Chip
                label={status}
                color={status === 'UP' ? 'success' : 'error'}
                size="medium"
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Application Info</Typography>
              {info?.app ? (
                <Stack spacing={0.5}>
                  <Typography variant="body2"><b>Name:</b> {(info as any).app?.name}</Typography>
                  <Typography variant="body2"><b>Version:</b> {(info as any).app?.version}</Typography>
                  <Typography variant="body2"><b>Description:</b> {(info as any).app?.description}</Typography>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">Loading…</Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Quick Actions</Typography>
              <Stack direction="row" spacing={2} flexWrap="wrap">
                <Button variant="contained" onClick={() => navigate('/generate')}>
                  Generate Banking Data
                </Button>
                <Button variant="outlined" onClick={() => navigate('/batch')}>
                  Run Batch Job
                </Button>
                <Button variant="outlined" onClick={() => navigate('/benchmark')}>
                  View Benchmarks
                </Button>
                <Button variant="outlined" href="/swagger-ui.html" target="_blank">
                  Swagger UI
                </Button>
                <Button variant="outlined" href="/actuator/health" target="_blank">
                  Actuator Health
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}
