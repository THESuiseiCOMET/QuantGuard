use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::thread::{self, JoinHandle};

pub(crate) struct RotatingLog {
    path: PathBuf,
    file: Option<File>,
    size: u64,
    limit: u64,
    backups: usize,
}

impl RotatingLog {
    pub(crate) fn open(path: PathBuf, limit: u64, backups: usize) -> io::Result<Self> {
        if limit == 0 || backups == 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid log limits",
            ));
        }
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        // Older versions appended forever. Retain only the tail of oversized logs.
        for index in 0..=backups {
            let candidate = if index == 0 {
                path.clone()
            } else {
                backup_path(&path, index)
            };
            trim_tail(&candidate, limit)?;
        }
        let file = OpenOptions::new().create(true).append(true).open(&path)?;
        let size = file.metadata()?.len();
        Ok(Self {
            path,
            file: Some(file),
            size,
            limit,
            backups,
        })
    }

    fn rotate(&mut self) -> io::Result<()> {
        self.file.take();
        remove_if_present(&backup_path(&self.path, self.backups))?;
        for index in (1..self.backups).rev() {
            rename_if_present(
                &backup_path(&self.path, index),
                &backup_path(&self.path, index + 1),
            )?;
        }
        rename_if_present(&self.path, &backup_path(&self.path, 1))?;
        self.file = Some(
            OpenOptions::new()
                .create(true)
                .append(true)
                .open(&self.path)?,
        );
        self.size = 0;
        Ok(())
    }
}

impl Write for RotatingLog {
    fn write(&mut self, mut bytes: &[u8]) -> io::Result<usize> {
        let length = bytes.len();
        while !bytes.is_empty() {
            if self.size >= self.limit {
                self.rotate()?;
            }
            let count = bytes.len().min((self.limit - self.size) as usize);
            let file = self
                .file
                .as_mut()
                .ok_or_else(|| io::Error::other("log is closed"))?;
            file.write_all(&bytes[..count])?;
            self.size += count as u64;
            bytes = &bytes[count..];
        }
        Ok(length)
    }

    fn flush(&mut self) -> io::Result<()> {
        if let Some(file) = self.file.as_mut() {
            file.flush()?;
        }
        Ok(())
    }
}

fn backup_path(path: &Path, index: usize) -> PathBuf {
    let mut name = path.as_os_str().to_os_string();
    name.push(format!(".{index}"));
    PathBuf::from(name)
}

fn trim_tail(path: &Path, limit: u64) -> io::Result<()> {
    let mut file = match OpenOptions::new().read(true).write(true).open(path) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error),
    };
    let length = file.metadata()?.len();
    if length > limit {
        file.seek(SeekFrom::Start(length - limit))?;
        let mut tail = Vec::with_capacity(limit as usize);
        file.read_to_end(&mut tail)?;
        file.seek(SeekFrom::Start(0))?;
        file.write_all(&tail)?;
        file.set_len(limit)?;
    }
    Ok(())
}

fn remove_if_present(path: &Path) -> io::Result<()> {
    match fs::remove_file(path) {
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        result => result,
    }
}

fn rename_if_present(from: &Path, to: &Path) -> io::Result<()> {
    match fs::rename(from, to) {
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        result => result,
    }
}

pub(crate) fn drain_log(
    reader: impl Read + Send + 'static,
    mut log: RotatingLog,
) -> JoinHandle<()> {
    thread::spawn(move || {
        let mut reader = reader;
        let mut buffer = [0; 8192];
        loop {
            match reader.read(&mut buffer) {
                Ok(0) => break,
                Ok(count) => {
                    // Keep draining if disk writes fail so a full pipe cannot stall a service.
                    let _ = log.write_all(&buffer[..count]);
                }
                Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
                Err(_) => break,
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;
    use std::time::{SystemTime, UNIX_EPOCH};

    struct Directory(PathBuf);

    impl Directory {
        fn new() -> Self {
            let nonce = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos();
            let path = std::env::temp_dir().join(format!(
                "quantguard-log-test-{}-{nonce}",
                std::process::id()
            ));
            fs::create_dir(&path).unwrap();
            Self(path)
        }
    }

    impl Drop for Directory {
        fn drop(&mut self) {
            assert!(self.0.starts_with(std::env::temp_dir()));
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn huge_write_keeps_only_bounded_latest_chunks() {
        let dir = Directory::new();
        let path = dir.0.join("backend.out.log");
        let mut log = RotatingLog::open(path.clone(), 4, 2).unwrap();
        log.write_all(b"aaaabbbbccccdddde").unwrap();
        drop(log);
        assert_eq!(fs::read(&path).unwrap(), b"e");
        assert_eq!(fs::read(backup_path(&path, 1)).unwrap(), b"dddd");
        assert_eq!(fs::read(backup_path(&path, 2)).unwrap(), b"cccc");
        assert!(!backup_path(&path, 3).exists());
    }

    #[test]
    fn opening_old_oversized_logs_retains_tails_and_leaves_data_alone() {
        let dir = Directory::new();
        let path = dir.0.join("desktop-runtime.log");
        let sentinel = dir.0.join("quantguard.mv.db");
        fs::write(&sentinel, b"database sentinel").unwrap();
        fs::write(&path, b"old textLATEST").unwrap();
        fs::write(backup_path(&path, 1), b"old backupRECENT").unwrap();
        drop(RotatingLog::open(path.clone(), 6, 2).unwrap());
        assert_eq!(fs::read(&path).unwrap(), b"LATEST");
        assert_eq!(fs::read(backup_path(&path, 1)).unwrap(), b"RECENT");
        assert_eq!(fs::read(sentinel).unwrap(), b"database sentinel");
    }

    #[test]
    fn pipe_drain_rotates_without_truncating_each_input_chunk() {
        let dir = Directory::new();
        let path = dir.0.join("market-data.err.log");
        let log = RotatingLog::open(path.clone(), 5, 2).unwrap();
        drain_log(Cursor::new(b"12345678901".to_vec()), log)
            .join()
            .unwrap();
        assert_eq!(fs::read(&path).unwrap(), b"1");
        assert_eq!(fs::read(backup_path(&path, 1)).unwrap(), b"67890");
        assert_eq!(fs::read(backup_path(&path, 2)).unwrap(), b"12345");
    }

    #[test]
    fn restart_continues_current_log_and_rotates_at_limit() {
        let dir = Directory::new();
        let path = dir.0.join("backend.err.log");
        let mut first = RotatingLog::open(path.clone(), 4, 2).unwrap();
        first.write_all(b"ab").unwrap();
        drop(first);
        let mut second = RotatingLog::open(path.clone(), 4, 2).unwrap();
        second.write_all(b"cde").unwrap();
        drop(second);
        assert_eq!(fs::read(&path).unwrap(), b"e");
        assert_eq!(fs::read(backup_path(&path, 1)).unwrap(), b"abcd");
    }
}
