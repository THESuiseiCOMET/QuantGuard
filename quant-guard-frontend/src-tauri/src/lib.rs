mod rotating_log;
mod runtime;

use runtime::{DesktopConfig, DesktopServiceManager, RuntimeSnapshot, RuntimeStage};
use serde_json::{Value, json};
use std::fs;
use std::net::{Ipv4Addr, TcpListener};
use std::path::PathBuf;
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager, RunEvent, State};

#[tauri::command]
fn get_desktop_runtime_status(manager: State<'_, Arc<DesktopServiceManager>>) -> RuntimeSnapshot {
    manager.snapshot()
}

#[tauri::command]
fn get_backend_base_url(manager: State<'_, Arc<DesktopServiceManager>>) -> Result<String, String> {
    let snapshot = manager.snapshot();
    if snapshot.status != RuntimeStage::Ready {
        return Err("QuantGuard desktop services are not ready".to_string());
    }
    snapshot
        .backend_base_url
        .ok_or_else(|| "Backend address is unavailable".to_string())
}

#[tauri::command]
fn retry_desktop_services(manager: State<'_, Arc<DesktopServiceManager>>) -> RuntimeSnapshot {
    manager.inner().retry();
    manager.snapshot()
}

#[tauri::command]
fn open_log_directory(manager: State<'_, Arc<DesktopServiceManager>>) -> Result<(), String> {
    std::process::Command::new("explorer.exe")
        .arg(manager.inner().snapshot().log_directory)
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("Unable to open the log directory: {error}"))
}

#[tauri::command]
fn exit_desktop_app(app: AppHandle, manager: State<'_, Arc<DesktopServiceManager>>) {
    manager.inner().shutdown();
    app.exit(0);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .setup(|app| {
            let config = DesktopConfig::from_app(app.handle())
                .map_err(|error| -> Box<dyn std::error::Error> { Box::new(error) })?;
            let manager = DesktopServiceManager::new(config);
            app.manage(Arc::clone(&manager));
            manager.start_async();
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_desktop_runtime_status,
            get_backend_base_url,
            retry_desktop_services,
            open_log_directory,
            exit_desktop_app
        ]);

    let app = builder
        .build(tauri::generate_context!())
        .expect("error while building QuantGuard desktop application");
    app.run(|app_handle, event| {
        if let RunEvent::ExitRequested { api, .. } = event {
            let manager = app_handle.state::<Arc<DesktopServiceManager>>();
            if !manager.is_stopping_or_stopped() {
                api.prevent_exit();
                let manager = Arc::clone(manager.inner());
                let app = app_handle.clone();
                thread::spawn(move || {
                    manager.shutdown();
                    app.exit(0);
                });
            }
        }
    });
}

pub fn run_runtime_smoke_from_args() -> Option<i32> {
    let args: Vec<String> = std::env::args().collect();
    if !args.iter().any(|arg| arg == "--runtime-smoke") {
        return None;
    }
    Some(match run_runtime_smoke(&args) {
        Ok(()) => 0,
        Err(error) => {
            eprintln!("QuantGuard runtime smoke failed: {error}");
            1
        }
    })
}

fn run_runtime_smoke(args: &[String]) -> Result<(), String> {
    let runtime_root = argument_value(args, "--resource-root")?;
    let data_root = argument_value(args, "--data-root")?;
    let simulate = optional_argument_value(args, "--simulate-failure");
    let occupy_defaults = args.iter().any(|arg| arg == "--occupy-default-ports");
    let _default_python = occupy_defaults
        .then(|| TcpListener::bind((Ipv4Addr::LOCALHOST, 8001)))
        .transpose()
        .map_err(|error| format!("cannot occupy port 8001: {error}"))?;
    let _default_backend = occupy_defaults
        .then(|| TcpListener::bind((Ipv4Addr::LOCALHOST, 8080)))
        .transpose()
        .map_err(|error| format!("cannot occupy port 8080: {error}"))?;

    let mut config = DesktopConfig::new(PathBuf::from(runtime_root), PathBuf::from(data_root))
        .map_err(|error| error.to_string())?;
    match simulate.as_deref() {
        Some("python") => config.python_executable = config.runtime_root.join("missing-python.exe"),
        Some("backend") => {
            config.backend_executable = config.runtime_root.join("missing-backend.exe")
        }
        Some(other) => return Err(format!("unsupported failure stage: {other}")),
        None => {}
    }
    if simulate.is_some() {
        // Failure injection intentionally bypasses the executable existence preflight.
        fs::create_dir_all(config.log_root()).map_err(|error| error.to_string())?;
    }
    let manager = DesktopServiceManager::new(config);
    manager.start_async();

    if let Some(stage) = simulate {
        let snapshot = wait_for_terminal_state(&manager, Duration::from_secs(75))?;
        if snapshot.status != RuntimeStage::Failed {
            return Err(format!("{stage} failure injection did not fail"));
        }
        manager.shutdown();
        println!("failure injection passed: {stage}");
        return Ok(());
    }

    let first = wait_for_ready(&manager, Duration::from_secs(100))?;
    if occupy_defaults && (first.python_port == Some(8001) || first.backend_port == Some(8080)) {
        return Err("desktop services reused an occupied default port".to_string());
    }
    let backend = first
        .backend_base_url
        .clone()
        .ok_or_else(|| "backend address is missing".to_string())?;
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|error| error.to_string())?;
    let account_name = "tauri-runtime-smoke-account";
    let response = client
        .post(format!("{backend}/api/accounts"))
        .json(&json!({
            "accountName": account_name,
            "accountType": "LONG_TERM",
            "initialCash": 100000
        }))
        .send()
        .map_err(|error| error.to_string())?;
    if !response.status().is_success() {
        return Err(format!(
            "account creation failed with {}",
            response.status()
        ));
    }
    manager.shutdown();
    assert_ports_released(&first)?;

    manager.start_async();
    let second = wait_for_ready(&manager, Duration::from_secs(100))?;
    let backend = second
        .backend_base_url
        .clone()
        .ok_or_else(|| "backend address is missing after restart".to_string())?;
    let accounts: Value = client
        .get(format!("{backend}/api/accounts"))
        .send()
        .and_then(|response| response.json())
        .map_err(|error| error.to_string())?;
    let persisted = accounts["data"]
        .as_array()
        .is_some_and(|items| items.iter().any(|item| item["accountName"] == account_name));
    if !persisted {
        return Err("desktop data did not persist across restart".to_string());
    }
    manager.shutdown();
    assert_ports_released(&second)?;
    println!(
        "runtime smoke passed: first ports={:?}/{:?}, restart ports={:?}/{:?}",
        first.python_port, first.backend_port, second.python_port, second.backend_port
    );
    Ok(())
}

fn argument_value(args: &[String], key: &str) -> Result<String, String> {
    optional_argument_value(args, key).ok_or_else(|| format!("{key} is required"))
}

fn optional_argument_value(args: &[String], key: &str) -> Option<String> {
    args.iter()
        .position(|arg| arg == key)
        .and_then(|index| args.get(index + 1))
        .cloned()
}

fn wait_for_ready(
    manager: &Arc<DesktopServiceManager>,
    timeout: Duration,
) -> Result<RuntimeSnapshot, String> {
    let snapshot = wait_for_terminal_state(manager, timeout)?;
    if snapshot.status == RuntimeStage::Ready {
        Ok(snapshot)
    } else {
        Err(snapshot
            .error
            .unwrap_or_else(|| "desktop services failed".to_string()))
    }
}

fn wait_for_terminal_state(
    manager: &Arc<DesktopServiceManager>,
    timeout: Duration,
) -> Result<RuntimeSnapshot, String> {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
        let snapshot = manager.snapshot();
        if matches!(snapshot.status, RuntimeStage::Ready | RuntimeStage::Failed) {
            return Ok(snapshot);
        }
        thread::sleep(Duration::from_millis(200));
    }
    Err("desktop runtime smoke timed out".to_string())
}

fn assert_ports_released(snapshot: &RuntimeSnapshot) -> Result<(), String> {
    for port in [snapshot.python_port, snapshot.backend_port]
        .into_iter()
        .flatten()
    {
        TcpListener::bind((Ipv4Addr::LOCALHOST, port))
            .map_err(|error| format!("port {port} was not released: {error}"))?;
    }
    Ok(())
}
