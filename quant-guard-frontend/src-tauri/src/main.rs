// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    if let Some(exit_code) = quantguard_desktop_lib::run_runtime_smoke_from_args() {
        std::process::exit(exit_code);
    }
    quantguard_desktop_lib::run();
}
