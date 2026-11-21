use futures_util::{SinkExt, StreamExt};
use rustls::pki_types::ServerName;
use serde::{Deserialize, Serialize};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant};
use tokio::io::{AsyncRead, AsyncWrite, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::runtime::Runtime;
use tokio::sync::mpsc;
use tokio_rustls::{rustls::ClientConfig, TlsConnector};
use tokio_socks::tcp::Socks5Stream;
use tokio_tungstenite::{client_async, tungstenite::Message};
use url::Url;

pub trait AsyncStream: AsyncRead + AsyncWrite + Unpin + Send {}
impl<T: AsyncRead + AsyncWrite + Unpin + Send> AsyncStream for T {}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RouteProbeResult {
    pub ok: bool,
    pub rtt_ms: u64,
    pub mode: String,
    pub detail: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RelayRuntimeStatus {
    pub connected: bool,
    pub relay_url: String,
    pub socks_proxy: String,
    pub bytes_sent: u64,
    pub bytes_received: u64,
    pub last_error: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RelayEvent {
    pub event_type: String,
    pub payload: String,
}

static TOKIO_RT: OnceLock<Runtime> = OnceLock::new();

pub fn runtime() -> &'static Runtime {
    TOKIO_RT.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .expect("Tokio runtime init failed")
    })
}

struct RelayManager {
    tx: Option<mpsc::UnboundedSender<String>>,
    task_abort: Option<tokio::task::AbortHandle>,
    status: RelayRuntimeStatus,
    events: Vec<RelayEvent>,
}

static RELAY_STATE: OnceLock<Mutex<RelayManager>> = OnceLock::new();

fn relay_mgr() -> &'static Mutex<RelayManager> {
    RELAY_STATE.get_or_init(|| {
        Mutex::new(RelayManager {
            tx: None,
            task_abort: None,
            status: RelayRuntimeStatus {
                connected: false,
                relay_url: String::new(),
                socks_proxy: String::new(),
                bytes_sent: 0,
                bytes_received: 0,
                last_error: String::new(),
            },
            events: Vec::new(),
        })
    })
}

fn push_event(event_type: &str, payload: String) {
    if let Ok(mut g) = relay_mgr().lock() {
        if g.events.len() > 256 {
            g.events.remove(0);
        }
        g.events.push(RelayEvent {
            event_type: event_type.to_string(),
            payload,
        });
    }
}

pub fn build_tls_connector() -> Result<TlsConnector, String> {
    let _ = rustls::crypto::ring::default_provider().install_default();
    let mut root_store = rustls::RootCertStore::empty();
    root_store.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let config = ClientConfig::builder()
        .with_root_certificates(root_store)
        .with_no_client_auth();
    Ok(TlsConnector::from(Arc::new(config)))
}

async fn connect_raw_transport(
    target_host: &str,
    target_port: u16,
    socks_host: Option<&str>,
    socks_port: u16,
) -> Result<Box<dyn AsyncStream>, String> {
    if let Some(sh) = socks_host.filter(|s| !s.trim().is_empty()) {
        let proxy_addr = format!("{}:{}", sh.trim(), socks_port);
        let stream = tokio::time::timeout(
            Duration::from_secs(12),
            Socks5Stream::connect(proxy_addr.as_str(), (target_host, target_port)),
        )
        .await
        .map_err(|_| format!("SOCKS5 timeout connecting via {}", proxy_addr))?
        .map_err(|e| format!("SOCKS5 handshake via {} failed: {}", proxy_addr, e))?;
        Ok(Box::new(stream))
    } else {
        let addr = format!("{}:{}", target_host, target_port);
        let stream = tokio::time::timeout(Duration::from_secs(10), TcpStream::connect(&addr))
            .await
            .map_err(|_| format!("TCP timeout connecting to {}", addr))?
            .map_err(|e| format!("TCP connect to {} failed: {}", addr, e))?;
        Ok(Box::new(stream))
    }
}

pub fn probe_route(
    target_host: &str,
    target_port: u16,
    socks_host: &str,
    socks_port: u16,
    use_socks5: bool,
) -> RouteProbeResult {
    let start = Instant::now();
    let res = runtime().block_on(async {
        let socks_opt = if use_socks5 { Some(socks_host) } else { None };
        let mut stream = connect_raw_transport(target_host, target_port, socks_opt, socks_port).await?;
        let _ = stream.flush().await;
        let _ = stream.shutdown().await;
        Ok::<(), String>(())
    });
    let rtt = start.elapsed().as_millis() as u64;
    match res {
        Ok(()) => RouteProbeResult {
            ok: true,
            rtt_ms: rtt,
            mode: if use_socks5 {
                format!("SOCKS5({}:{})", socks_host, socks_port)
            } else {
                "DIRECT_TCP".to_string()
            },
            detail: format!(
                "Handshake verified to {}:{} in {}ms",
                target_host, target_port, rtt
            ),
        },
        Err(err) => RouteProbeResult {
            ok: false,
            rtt_ms: rtt,
            mode: if use_socks5 {
                format!("SOCKS5({}:{})", socks_host, socks_port)
            } else {
                "DIRECT_TCP".to_string()
            },
            detail: err,
        },
    }
}

pub fn probe_socks5_daemon(socks_host: &str, socks_port: u16) -> RouteProbeResult {
    let start = Instant::now();
    let addr = format!("{}:{}", socks_host.trim(), socks_port);
    let res = runtime().block_on(async {
        let mut stream = tokio::time::timeout(Duration::from_secs(3), TcpStream::connect(&addr))
            .await
            .map_err(|_| format!("Timeout probing SOCKS5 daemon {}", addr))?
            .map_err(|e| format!("SOCKS5 daemon {} unreachable: {}", addr, e))?;
        // Send SOCKS5 greeting [VER=5, NMETHODS=1, METHOD=NO_AUTH(0)]
        use tokio::io::AsyncReadExt;
        stream
            .write_all(&[0x05, 0x01, 0x00])
            .await
            .map_err(|e| e.to_string())?;
        let mut reply = [0u8; 2];
        tokio::time::timeout(Duration::from_secs(3), stream.read_exact(&mut reply))
            .await
            .map_err(|_| "SOCKS5 greeting reply timeout".to_string())?
            .map_err(|e| e.to_string())?;
        if reply[0] != 0x05 {
            return Err(format!("Invalid SOCKS version byte: {}", reply[0]));
        }
        Ok::<(), String>(())
    });
    let rtt = start.elapsed().as_millis() as u64;
    match res {
        Ok(()) => RouteProbeResult {
            ok: true,
            rtt_ms: rtt,
            mode: format!("SOCKS5_DAEMON({})", addr),
            detail: format!("Live Tor/SOCKS5 daemon verified at {} ({}ms)", addr, rtt),
        },
        Err(e) => RouteProbeResult {
            ok: false,
            rtt_ms: rtt,
            mode: format!("SOCKS5_DAEMON({})", addr),
            detail: e,
        },
    }
}

pub fn relay_connect(ws_url: &str, socks_host: Option<&str>, socks_port: u16) -> Result<(), String> {
    let parsed = Url::parse(ws_url).map_err(|e| format!("Invalid WebSocket URL: {}", e))?;
    let scheme = parsed.scheme().to_string();
    let host = parsed
        .host_str()
        .ok_or_else(|| "Missing WebSocket host".to_string())?
        .to_string();
    let port = parsed
        .port_or_known_default()
        .ok_or_else(|| "Unknown WebSocket port".to_string())?;

    relay_disconnect();

    let (tx, mut rx) = mpsc::unbounded_channel::<String>();
    let socks_owned = socks_host.map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    let url_owned = ws_url.to_string();

    if let Ok(mut g) = relay_mgr().lock() {
        g.tx = Some(tx);
        g.status.connected = false;
        g.status.relay_url = url_owned.clone();
        g.status.socks_proxy = socks_owned
            .as_ref()
            .map(|h| format!("{}:{}", h, socks_port))
            .unwrap_or_else(|| "DIRECT".to_string());
        g.status.last_error.clear();
    }

    let handle = runtime().spawn(async move {
        let transport_res = connect_raw_transport(
            &host,
            port,
            socks_owned.as_deref(),
            socks_port,
        )
        .await;

        let raw_stream = match transport_res {
            Ok(s) => s,
            Err(e) => {
                if let Ok(mut g) = relay_mgr().lock() {
                    g.status.connected = false;
                    g.status.last_error = e.clone();
                }
                push_event("ERROR", e);
                return;
            }
        };

        let upgraded_stream: Box<dyn AsyncStream> = if scheme == "wss" {
            let connector = match build_tls_connector() {
                Ok(c) => c,
                Err(e) => {
                    if let Ok(mut g) = relay_mgr().lock() {
                        g.status.last_error = e.clone();
                    }
                    push_event("ERROR", e);
                    return;
                }
            };
            let server_name = match ServerName::try_from(host.clone()) {
                Ok(sn) => sn,
                Err(e) => {
                    let msg = format!("Invalid TLS server name: {}", e);
                    if let Ok(mut g) = relay_mgr().lock() {
                        g.status.last_error = msg.clone();
                    }
                    push_event("ERROR", msg);
                    return;
                }
            };
            match connector.connect(server_name, raw_stream).await {
                Ok(tls) => Box::new(tls),
                Err(e) => {
                    let msg = format!("TLS handshake failed: {}", e);
                    if let Ok(mut g) = relay_mgr().lock() {
                        g.status.last_error = msg.clone();
                    }
                    push_event("ERROR", msg);
                    return;
                }
            }
        } else {
            raw_stream
        };

        let ws_stream = match client_async(&url_owned, upgraded_stream).await {
            Ok((ws, _resp)) => ws,
            Err(e) => {
                let msg = format!("WebSocket upgrade failed: {}", e);
                if let Ok(mut g) = relay_mgr().lock() {
                    g.status.connected = false;
                    g.status.last_error = msg.clone();
                }
                push_event("ERROR", msg);
                return;
            }
        };

        if let Ok(mut g) = relay_mgr().lock() {
            g.status.connected = true;
            g.status.last_error.clear();
        }
        push_event("CONNECTED", url_owned.clone());

        let (mut write, mut read) = ws_stream.split();

        loop {
            tokio::select! {
                Some(outgoing) = rx.recv() => {
                    let len = outgoing.len() as u64;
                    if let Err(e) = write.send(Message::Text(outgoing.into())).await {
                        let msg = format!("WebSocket write failed: {}", e);
                        if let Ok(mut g) = relay_mgr().lock() {
                            g.status.connected = false;
                            g.status.last_error = msg.clone();
                        }
                        push_event("DISCONNECTED", msg);
                        break;
                    }
                    if let Ok(mut g) = relay_mgr().lock() {
                        g.status.bytes_sent += len;
                    }
                }
                incoming = read.next() => {
                    match incoming {
                        Some(Ok(Message::Text(txt))) => {
                            let s = txt.to_string();
                            let len = s.len() as u64;
                            if let Ok(mut g) = relay_mgr().lock() {
                                g.status.bytes_received += len;
                            }
                            push_event("MESSAGE", s);
                        }
                        Some(Ok(Message::Binary(bin))) => {
                            let len = bin.len() as u64;
                            if let Ok(mut g) = relay_mgr().lock() {
                                g.status.bytes_received += len;
                            }
                            if let Ok(s) = String::from_utf8(bin.to_vec()) {
                                push_event("MESSAGE", s);
                            }
                        }
                        Some(Ok(Message::Close(_))) | None => {
                            if let Ok(mut g) = relay_mgr().lock() {
                                g.status.connected = false;
                            }
                            push_event("DISCONNECTED", "Relay closed connection".to_string());
                            break;
                        }
                        Some(Err(e)) => {
                            let msg = format!("WebSocket read error: {}", e);
                            if let Ok(mut g) = relay_mgr().lock() {
                                g.status.connected = false;
                                g.status.last_error = msg.clone();
                            }
                            push_event("DISCONNECTED", msg);
                            break;
                        }
                        _ => {}
                    }
                }
            }
        }
    });

    if let Ok(mut g) = relay_mgr().lock() {
        g.task_abort = Some(handle.abort_handle());
    }

    Ok(())
}

pub fn relay_send_frame(frame_json: &str) -> Result<(), String> {
    let tx = relay_mgr()
        .lock()
        .map_err(|_| "Relay lock poisoned".to_string())?
        .tx
        .clone();
    match tx {
        Some(sender) => sender
            .send(frame_json.to_string())
            .map_err(|e| format!("Relay send channel closed: {}", e)),
        None => Err("Relay is not connected".to_string()),
    }
}

pub fn relay_disconnect() {
    if let Ok(mut g) = relay_mgr().lock() {
        if let Some(abort) = g.task_abort.take() {
            abort.abort();
        }
        g.tx = None;
        g.status.connected = false;
    }
}

pub fn relay_status() -> RelayRuntimeStatus {
    relay_mgr()
        .lock()
        .map(|g| g.status.clone())
        .unwrap_or(RelayRuntimeStatus {
            connected: false,
            relay_url: String::new(),
            socks_proxy: String::new(),
            bytes_sent: 0,
            bytes_received: 0,
            last_error: "Lock error".to_string(),
        })
}

pub fn relay_drain_events() -> Vec<RelayEvent> {
    if let Ok(mut g) = relay_mgr().lock() {
        let out = g.events.clone();
        g.events.clear();
        out
    } else {
        Vec::new()
    }
}
