import { reactive } from 'vue'
import { setApiBaseURL } from '@/api/http'

export type DesktopRuntimeStage =
  | 'STARTING'
  | 'PYTHON_STARTING'
  | 'PYTHON_READY'
  | 'BACKEND_STARTING'
  | 'READY'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED'

export interface DesktopRuntimeSnapshot {
  status: DesktopRuntimeStage
  message: string
  failureStage: string | null
  error: string | null
  backendBaseUrl: string | null
  pythonPort: number | null
  backendPort: number | null
  logDirectory: string
}

interface DesktopRuntimeState {
  isDesktop: boolean
  isReady: boolean
  snapshot: DesktopRuntimeSnapshot | null
  bridgeError: string
}

export const desktopRuntime = reactive<DesktopRuntimeState>({
  isDesktop: '__TAURI_INTERNALS__' in window,
  isReady: false,
  snapshot: null,
  bridgeError: '',
})

let pollGeneration = 0

export async function initializeDesktopRuntime() {
  if (!desktopRuntime.isDesktop) {
    desktopRuntime.isReady = true
    return
  }
  pollGeneration += 1
  await pollRuntime(pollGeneration)
}

export async function retryDesktopRuntime() {
  desktopRuntime.bridgeError = ''
  desktopRuntime.isReady = false
  const { invoke } = await import('@tauri-apps/api/core')
  desktopRuntime.snapshot = await invoke<DesktopRuntimeSnapshot>('retry_desktop_services')
  pollGeneration += 1
  await pollRuntime(pollGeneration)
}

export async function openDesktopLogs() {
  const { invoke } = await import('@tauri-apps/api/core')
  await invoke('open_log_directory')
}

export async function exitDesktopApplication() {
  const { invoke } = await import('@tauri-apps/api/core')
  await invoke('exit_desktop_app')
}

async function pollRuntime(generation: number) {
  const { invoke } = await import('@tauri-apps/api/core')
  while (generation === pollGeneration) {
    try {
      const snapshot = await invoke<DesktopRuntimeSnapshot>('get_desktop_runtime_status')
      desktopRuntime.snapshot = snapshot
      desktopRuntime.bridgeError = ''
      if (snapshot.status === 'READY') {
        const backendBaseURL = await invoke<string>('get_backend_base_url')
        setApiBaseURL(backendBaseURL)
        desktopRuntime.isReady = true
        return
      }
      if (snapshot.status === 'FAILED') {
        desktopRuntime.isReady = false
        return
      }
    } catch (error) {
      desktopRuntime.bridgeError = error instanceof Error ? error.message : '无法读取桌面服务状态'
      return
    }
    await delay(300)
  }
}

function delay(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}
