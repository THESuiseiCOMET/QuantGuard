use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::net::{IpAddr, Ipv4Addr, SocketAddr, TcpListener};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Manager};
use thiserror::Error;
use uuid::Uuid;

#[cfg(windows)]
use std::os::windows::io::AsRawHandle;
#[cfg(windows)]
use windows_sys::Win32::Foundation::{CloseHandle, HANDLE};
#[cfg(windows)]
use windows_sys::Win32::System::JobObjects::{
    AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE,
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JobObjectExtendedLimitInformation,
    SetInformationJobObject, TerminateJobObject,
};

const PYTHON_TIMEOUT: Duration = Duration::from_secs(30);
const BACKEND_TIMEOUT: Duration = Duration::from_secs(60);
const SHUTDOWN_TIMEOUT: Duration = Duration::from_secs(8);
const HEALTH_INTERVAL: Duration = Duration::from_millis(250);
const PORT_ATTEMPTS: usize = 3;

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RuntimeStage {
    Starting,
    PythonStarting,
    PythonReady,
    BackendStarting,
    Ready,
    Stopping,
    Stopped,
    Failed,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeSnapshot {
    pub status: RuntimeStage,
    pub message: String,
    pub failure_stage: Option<String>,
    pub error: Option<String>,
    pub backend_base_url: Option<String>,
    pub python_port: Option<u16>,
    pub backend_port: Option<u16>,
    pub log_directory: String,
}

#[derive(Clone, Debug)]
pub struct DesktopConfig {
    pub(crate) runtime_root: PathBuf,
    pub(crate) data_root: PathBuf,
    pub(crate) log_root: PathBuf,
    pub(crate) python_executable: PathBuf,
    pub(crate) backend_executable: PathBuf,
    pub(crate) shutdown_token: String,
}

impl DesktopConfig {
    pub fn from_app(app: &AppHandle) -> Result<Self, RuntimeError> {
        let manifest_staging = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("runtime-staging");
        let runtime_root = if cfg!(debug_assertions) && manifest_staging.is_dir() {
            manifest_staging
        } else {
            app.path()
                .resource_dir()
                .map_err(|error| RuntimeError::Configuration(error.to_string()))?
                .join("runtime")
        };
        let data_root = std::env::var_os("QUANTGUARD_DESKTOP_DATA_DIR")
            .map(PathBuf::from)
            .or_else(|| {
                std::env::var_os("APPDATA").map(|value| PathBuf::from(value).join("QuantGuard"))
            })
            .ok_or_else(|| {
                RuntimeError::Configuration(
                    "Windows APPDATA is unavailable; the desktop data directory cannot be resolved"
                        .to_string(),
                )
            })?;
        Self::new(runtime_root, data_root)
    }

    pub fn new(runtime_root: PathBuf, data_root: PathBuf) -> Result<Self, RuntimeError> {
        let log_root = data_root.join("logs");
        let config = Self {
            python_executable: runtime_root
                .join("market-data")
                .join("quantguard-market-data.exe"),
            backend_executable: runtime_root.join("backend").join("QuantGuardBackend.exe"),
            runtime_root,
            data_root,
            log_root,
            shutdown_token: generate_shutdown_token(),
        };
        Ok(config)
    }

    fn prepare(&self) -> Result<(), RuntimeError> {
        for path in [
            &self.data_root,
            &self.log_root,
            &self.data_root.join("data"),
            &self.data_root.join("config"),
            &self.data_root.join("runtime"),
            &self.log_root.join("market-data"),
        ] {
            fs::create_dir_all(path).map_err(|error| RuntimeError::Directory {
                path: path.clone(),
                source: error,
            })?;
        }
        let write_probe = self.data_root.join("runtime").join(".write-test");
        fs::write(&write_probe, b"QuantGuard")
            .and_then(|_| fs::remove_file(&write_probe))
            .map_err(|error| RuntimeError::Directory {
                path: self.data_root.clone(),
                source: error,
            })?;
        for executable in [&self.python_executable, &self.backend_executable] {
            if !executable.is_file() {
                return Err(RuntimeError::MissingRuntime(executable.clone()));
            }
        }
        Ok(())
    }

    pub fn log_root(&self) -> &Path {
        &self.log_root
    }
}

#[derive(Debug, Error)]
pub enum RuntimeError {
    #[error("desktop configuration failed: {0}")]
    Configuration(String),
    #[error("desktop data directory is unavailable: {path}: {source}")]
    Directory { path: PathBuf, source: io::Error },
    #[error("packaged runtime is missing: {0}")]
    MissingRuntime(PathBuf),
    #[error("failed to allocate a loopback port: {0}")]
    Port(io::Error),
    #[error("failed to start {service}: {source}")]
    Spawn { service: String, source: io::Error },
    #[error("{service} exited before becoming ready with code {exit_code:?}")]
    EarlyExit {
        service: String,
        exit_code: Option<i32>,
    },
    #[error("{service} did not become healthy within {seconds} seconds")]
    HealthTimeout { service: String, seconds: u64 },
    #[error("Windows process-tree containment failed: {0}")]
    Job(String),
    #[error("desktop service startup was cancelled")]
    Cancelled,
}

struct RuntimeInner {
    snapshot: RuntimeSnapshot,
    python: Option<ManagedChild>,
    backend: Option<ManagedChild>,
}

pub struct DesktopServiceManager {
    config: DesktopConfig,
    client: Client,
    inner: Mutex<RuntimeInner>,
}

impl DesktopServiceManager {
    pub fn new(config: DesktopConfig) -> Arc<Self> {
        let snapshot = RuntimeSnapshot {
            status: RuntimeStage::Stopped,
            message: "QuantGuard desktop services are stopped".to_string(),
            failure_stage: None,
            error: None,
            backend_base_url: None,
            python_port: None,
            backend_port: None,
            log_directory: config.log_root.to_string_lossy().into_owned(),
        };
        Arc::new(Self {
            config,
            client: Client::builder()
                .connect_timeout(Duration::from_secs(2))
                .timeout(Duration::from_secs(2))
                .build()
                .expect("local HTTP client configuration must be valid"),
            inner: Mutex::new(RuntimeInner {
                snapshot,
                python: None,
                backend: None,
            }),
        })
    }

    pub fn snapshot(&self) -> RuntimeSnapshot {
        self.inner
            .lock()
            .expect("desktop runtime lock poisoned")
            .snapshot
            .clone()
    }

    pub fn start_async(self: &Arc<Self>) -> bool {
        {
            let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
            if matches!(
                inner.snapshot.status,
                RuntimeStage::Starting
                    | RuntimeStage::PythonStarting
                    | RuntimeStage::PythonReady
                    | RuntimeStage::BackendStarting
                    | RuntimeStage::Ready
                    | RuntimeStage::Stopping
            ) {
                return false;
            }
            inner.snapshot.status = RuntimeStage::Starting;
            inner.snapshot.message = "Preparing QuantGuard desktop services".to_string();
            inner.snapshot.failure_stage = None;
            inner.snapshot.error = None;
            inner.snapshot.backend_base_url = None;
            inner.snapshot.python_port = None;
            inner.snapshot.backend_port = None;
        }
        let manager = Arc::clone(self);
        thread::spawn(move || {
            if let Err(error) = manager.start_services() {
                let failure_stage = manager.snapshot().status;
                manager.stop_children();
                manager.update_snapshot(|snapshot| {
                    snapshot.status = RuntimeStage::Failed;
                    snapshot.message = "QuantGuard desktop services failed to start".to_string();
                    snapshot.failure_stage = Some(format!("{failure_stage:?}"));
                    snapshot.error = Some(error.to_string());
                    snapshot.backend_base_url = None;
                });
                manager.append_log(&format!("startup failed at {failure_stage:?}: {error}"));
            }
        });
        true
    }

    pub fn retry(self: &Arc<Self>) -> bool {
        self.shutdown();
        self.start_async()
    }

    pub fn shutdown(&self) {
        let should_stop = {
            let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
            if matches!(
                inner.snapshot.status,
                RuntimeStage::Stopped | RuntimeStage::Stopping
            ) {
                false
            } else {
                inner.snapshot.status = RuntimeStage::Stopping;
                inner.snapshot.message = "Stopping QuantGuard desktop services".to_string();
                true
            }
        };
        if !should_stop {
            return;
        }
        self.stop_children();
        self.update_snapshot(|snapshot| {
            snapshot.status = RuntimeStage::Stopped;
            snapshot.message = "QuantGuard desktop services are stopped".to_string();
            snapshot.backend_base_url = None;
        });
        self.append_log("desktop services stopped");
    }

    pub fn is_stopping_or_stopped(&self) -> bool {
        matches!(
            self.snapshot().status,
            RuntimeStage::Stopping | RuntimeStage::Stopped
        )
    }

    fn start_services(&self) -> Result<(), RuntimeError> {
        self.config.prepare()?;
        self.update_snapshot(|snapshot| {
            snapshot.status = RuntimeStage::PythonStarting;
            snapshot.message = "Starting local market-data service".to_string();
        });
        let python_port = self.start_with_retries(ServiceKind::Python)?;
        self.update_snapshot(|snapshot| {
            snapshot.status = RuntimeStage::PythonReady;
            snapshot.message = "Local market-data service is ready".to_string();
            snapshot.python_port = Some(python_port);
        });

        self.update_snapshot(|snapshot| {
            snapshot.status = RuntimeStage::BackendStarting;
            snapshot.message = "Starting local database and backend".to_string();
        });
        let backend_port = self.start_with_retries(ServiceKind::Backend)?;
        self.update_snapshot(|snapshot| {
            snapshot.status = RuntimeStage::Ready;
            snapshot.message = "QuantGuard is ready".to_string();
            snapshot.backend_port = Some(backend_port);
            snapshot.backend_base_url = Some(format!("http://127.0.0.1:{backend_port}"));
        });
        self.append_log(&format!(
            "desktop services ready: market-data={python_port}, backend={backend_port}"
        ));
        Ok(())
    }

    fn start_with_retries(&self, kind: ServiceKind) -> Result<u16, RuntimeError> {
        let mut last_error = None;
        for attempt in 1..=PORT_ATTEMPTS {
            self.ensure_not_cancelled()?;
            let port = allocate_loopback_port()?;
            let child = match kind {
                ServiceKind::Python => self.spawn_python(port),
                ServiceKind::Backend => self.spawn_backend(port),
            }?;
            {
                let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
                match kind {
                    ServiceKind::Python => inner.python = Some(child),
                    ServiceKind::Backend => inner.backend = Some(child),
                }
            }
            let health_url = match kind {
                ServiceKind::Python => format!("http://127.0.0.1:{port}/health"),
                ServiceKind::Backend => format!("http://127.0.0.1:{port}/api/health"),
            };
            let timeout = match kind {
                ServiceKind::Python => PYTHON_TIMEOUT,
                ServiceKind::Backend => BACKEND_TIMEOUT,
            };
            match self.wait_for_health(kind, &health_url, timeout) {
                Ok(()) => return Ok(port),
                Err(error) => {
                    last_error = Some(error);
                    self.stop_child(kind);
                    self.append_log(&format!(
                        "{} startup attempt {attempt}/{PORT_ATTEMPTS} failed",
                        kind.label()
                    ));
                }
            }
        }
        Err(last_error.expect("at least one startup attempt must run"))
    }

    fn spawn_python(&self, port: u16) -> Result<ManagedChild, RuntimeError> {
        let log_dir = self.config.log_root.join("market-data");
        let mut command = Command::new(&self.config.python_executable);
        command
            .arg("--host")
            .arg("127.0.0.1")
            .arg("--port")
            .arg(port.to_string())
            .arg("--log-dir")
            .arg(&log_dir);
        command.env(
            "QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN",
            &self.config.shutdown_token,
        );
        prepare_child_environment(&mut command);
        let child = spawn_managed(
            command,
            "market-data",
            &self.config.log_root,
            format!("http://127.0.0.1:{port}/internal/desktop/shutdown"),
            self.config.shutdown_token.clone(),
        )?;
        Ok(child)
    }

    fn spawn_backend(&self, port: u16) -> Result<ManagedChild, RuntimeError> {
        let python_port = self
            .snapshot()
            .python_port
            .ok_or_else(|| RuntimeError::Configuration("Python port is unavailable".to_string()))?;
        let mut command = Command::new(&self.config.backend_executable);
        command.arg(format!("--server.port={port}"));
        command.arg("--server.address=127.0.0.1");
        command.env("SPRING_PROFILES_ACTIVE", "desktop");
        command.env("QUANTGUARD_DATA_DIR", &self.config.data_root);
        command.env("MARKET_DATA_PROVIDER", "python");
        command.env(
            "PYTHON_MARKET_DATA_BASE_URL",
            format!("http://127.0.0.1:{python_port}"),
        );
        command.env(
            "QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN",
            &self.config.shutdown_token,
        );
        prepare_child_environment(&mut command);
        let child = spawn_managed(
            command,
            "backend",
            &self.config.log_root,
            format!("http://127.0.0.1:{port}/api/desktop/shutdown"),
            self.config.shutdown_token.clone(),
        )?;
        Ok(child)
    }

    fn wait_for_health(
        &self,
        kind: ServiceKind,
        url: &str,
        timeout: Duration,
    ) -> Result<(), RuntimeError> {
        let deadline = Instant::now() + timeout;
        while Instant::now() < deadline {
            self.ensure_not_cancelled()?;
            if let Ok(response) = self.client.get(url).send()
                && response.status().is_success()
            {
                return Ok(());
            }
            let exited = {
                let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
                let child = match kind {
                    ServiceKind::Python => inner.python.as_mut(),
                    ServiceKind::Backend => inner.backend.as_mut(),
                };
                child
                    .and_then(|managed| managed.child.try_wait().ok().flatten())
                    .map(|status| status.code())
            };
            if let Some(exit_code) = exited {
                return Err(RuntimeError::EarlyExit {
                    service: kind.label().to_string(),
                    exit_code,
                });
            }
            thread::sleep(HEALTH_INTERVAL);
        }
        Err(RuntimeError::HealthTimeout {
            service: kind.label().to_string(),
            seconds: timeout.as_secs(),
        })
    }

    fn ensure_not_cancelled(&self) -> Result<(), RuntimeError> {
        if matches!(self.snapshot().status, RuntimeStage::Stopping) {
            Err(RuntimeError::Cancelled)
        } else {
            Ok(())
        }
    }

    fn stop_child(&self, kind: ServiceKind) {
        let child = {
            let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
            match kind {
                ServiceKind::Python => inner.python.take(),
                ServiceKind::Backend => inner.backend.take(),
            }
        };
        if let Some(mut child) = child {
            child.stop(&self.client);
        }
    }

    fn stop_children(&self) {
        self.stop_child(ServiceKind::Backend);
        self.stop_child(ServiceKind::Python);
    }

    fn update_snapshot(&self, update: impl FnOnce(&mut RuntimeSnapshot)) {
        let mut inner = self.inner.lock().expect("desktop runtime lock poisoned");
        update(&mut inner.snapshot);
    }

    fn append_log(&self, message: &str) {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let path = self.config.log_root.join("desktop-runtime.log");
        if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
            let _ = writeln!(
                file,
                "{{\"timestamp\":{timestamp},\"message\":{message:?}}}"
            );
        }
    }
}

#[derive(Clone, Copy, Debug)]
enum ServiceKind {
    Python,
    Backend,
}

impl ServiceKind {
    fn label(self) -> &'static str {
        match self {
            Self::Python => "market-data service",
            Self::Backend => "backend",
        }
    }
}

struct ManagedChild {
    child: Child,
    job: JobObject,
    shutdown_url: String,
    shutdown_token: String,
}

impl ManagedChild {
    fn stop(&mut self, client: &Client) {
        let _ = client
            .post(&self.shutdown_url)
            .header("X-QuantGuard-Shutdown-Token", &self.shutdown_token)
            .send();
        let deadline = Instant::now() + SHUTDOWN_TIMEOUT;
        while Instant::now() < deadline {
            match self.child.try_wait() {
                Ok(Some(_)) => return,
                Ok(None) => thread::sleep(Duration::from_millis(100)),
                Err(_) => break,
            }
        }
        self.job.terminate();
        let _ = self.child.wait();
    }
}

fn spawn_managed(
    mut command: Command,
    name: &str,
    log_root: &Path,
    shutdown_url: String,
    shutdown_token: String,
) -> Result<ManagedChild, RuntimeError> {
    let stdout =
        create_process_log(log_root, name, "out").map_err(|source| RuntimeError::Spawn {
            service: name.to_string(),
            source,
        })?;
    let stderr =
        create_process_log(log_root, name, "err").map_err(|source| RuntimeError::Spawn {
            service: name.to_string(),
            source,
        })?;
    command.stdout(Stdio::from(stdout));
    command.stderr(Stdio::from(stderr));
    command.stdin(Stdio::null());
    let child = command.spawn().map_err(|source| RuntimeError::Spawn {
        service: name.to_string(),
        source,
    })?;
    let job = JobObject::new().and_then(|job| {
        job.assign(&child)?;
        Ok(job)
    })?;
    Ok(ManagedChild {
        child,
        job,
        shutdown_url,
        shutdown_token,
    })
}

fn create_process_log(log_root: &Path, name: &str, stream: &str) -> io::Result<File> {
    OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_root.join(format!("{name}.{stream}.log")))
}

fn prepare_child_environment(command: &mut Command) {
    let system_root = std::env::var("SystemRoot").unwrap_or_else(|_| r"C:\Windows".to_string());
    command.env("PATH", format!(r"{system_root}\System32"));
    command.env_remove("JAVA_HOME");
    command.env_remove("PYTHONHOME");
    command.env_remove("PYTHONPATH");
}

pub fn allocate_loopback_port() -> Result<u16, RuntimeError> {
    let address = SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 0);
    let listener = TcpListener::bind(address).map_err(RuntimeError::Port)?;
    listener
        .local_addr()
        .map(|address| address.port())
        .map_err(RuntimeError::Port)
}

fn generate_shutdown_token() -> String {
    Uuid::new_v4().to_string()
}

#[cfg(windows)]
struct JobObject {
    handle: HANDLE,
}

#[cfg(windows)]
unsafe impl Send for JobObject {}

#[cfg(windows)]
impl JobObject {
    fn new() -> Result<Self, RuntimeError> {
        let handle = unsafe { CreateJobObjectW(std::ptr::null(), std::ptr::null()) };
        if handle.is_null() {
            return Err(RuntimeError::Job(io::Error::last_os_error().to_string()));
        }
        let mut information: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = unsafe { std::mem::zeroed() };
        information.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        let configured = unsafe {
            SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                &information as *const _ as *const _,
                std::mem::size_of_val(&information) as u32,
            )
        };
        if configured == 0 {
            unsafe {
                CloseHandle(handle);
            }
            return Err(RuntimeError::Job(io::Error::last_os_error().to_string()));
        }
        Ok(Self { handle })
    }

    fn assign(&self, child: &Child) -> Result<(), RuntimeError> {
        let assigned =
            unsafe { AssignProcessToJobObject(self.handle, child.as_raw_handle() as HANDLE) };
        if assigned == 0 {
            return Err(RuntimeError::Job(io::Error::last_os_error().to_string()));
        }
        Ok(())
    }

    fn terminate(&self) {
        unsafe {
            TerminateJobObject(self.handle, 1);
        }
    }
}

#[cfg(windows)]
impl Drop for JobObject {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.handle);
        }
    }
}

#[cfg(not(windows))]
struct JobObject;

#[cfg(not(windows))]
impl JobObject {
    fn new() -> Result<Self, RuntimeError> {
        Ok(Self)
    }

    fn assign(&self, _child: &Child) -> Result<(), RuntimeError> {
        Ok(())
    }

    fn terminate(&self) {}
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allocated_port_is_loopback_and_available() {
        let port = allocate_loopback_port().expect("port should be allocated");
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, port))
            .expect("released port should be reusable");
        assert_eq!(
            listener.local_addr().expect("local address").ip(),
            IpAddr::V4(Ipv4Addr::LOCALHOST)
        );
    }

    #[test]
    fn runtime_stage_uses_stable_external_names() {
        assert_eq!(
            serde_json::to_string(&RuntimeStage::BackendStarting).unwrap(),
            "\"BACKEND_STARTING\""
        );
    }

    #[test]
    fn shutdown_token_is_nonempty_and_process_scoped() {
        let first = generate_shutdown_token();
        let second = generate_shutdown_token();
        assert!(!first.is_empty());
        assert_ne!(first, second);
    }
}
