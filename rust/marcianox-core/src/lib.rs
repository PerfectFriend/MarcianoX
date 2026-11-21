pub mod crypto;
pub mod network;
pub mod vault;

use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;

fn jstr_to_rust(env: &mut JNIEnv, s: JString) -> Result<String, String> {
    env.get_string(&s)
        .map(|js| js.into())
        .map_err(|e| format!("JNI get_string error: {}", e))
}

fn json_ok<T: Serialize>(data: &T) -> String {
    serde_json::json!({ "ok": true, "data": data }).to_string()
}

fn json_err(err: &str) -> String {
    serde_json::json!({ "ok": false, "error": err }).to_string()
}

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, "marcianox-core/1.0.0 (Rust + Tokio + Rustls + Zeroize)")
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeHashPinArgon2(
    mut env: JNIEnv,
    _class: JClass,
    pin: JString,
) -> jstring {
    let out = match jstr_to_rust(&mut env, pin) {
        Ok(p) => match crypto::hash_pin_argon2(&p) {
            Ok(rec) => json_ok(&rec),
            Err(e) => json_err(&e),
        },
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeVerifyPinArgon2(
    mut env: JNIEnv,
    _class: JClass,
    pin: JString,
    phc_hash: JString,
) -> jboolean {
    let p = match jstr_to_rust(&mut env, pin) {
        Ok(v) => v,
        Err(_) => return JNI_FALSE,
    };
    let h = match jstr_to_rust(&mut env, phc_hash) {
        Ok(v) => v,
        Err(_) => return JNI_FALSE,
    };
    if crypto::verify_pin_argon2(&p, &h) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeDeriveKeyPbkdf2(
    mut env: JNIEnv,
    _class: JClass,
    secret: JString,
    salt_b64: JString,
    iterations: jint,
) -> jbyteArray {
    let s = match jstr_to_rust(&mut env, secret) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let salt = match jstr_to_rust(&mut env, salt_b64) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    match crypto::derive_key_pbkdf2(&s, &salt, iterations.max(10_000) as u32) {
        Ok(key) => env
            .byte_array_from_slice(&key)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeEncryptAesGcm(
    mut env: JNIEnv,
    _class: JClass,
    plaintext: JByteArray,
    key: JByteArray,
) -> jstring {
    let pt = match env.convert_byte_array(plaintext) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e.to_string())),
    };
    let k = match env.convert_byte_array(key) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e.to_string())),
    };
    let out = match crypto::encrypt_aes_gcm(&pt, &k) {
        Ok(p) => json_ok(&p),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeDecryptAesGcm(
    mut env: JNIEnv,
    _class: JClass,
    ciphertext_b64: JString,
    iv_b64: JString,
    key: JByteArray,
) -> jbyteArray {
    let ct = match jstr_to_rust(&mut env, ciphertext_b64) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let iv = match jstr_to_rust(&mut env, iv_b64) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let k = match env.convert_byte_array(key) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    match crypto::decrypt_aes_gcm(&ct, &iv, &k) {
        Ok(pt) => env
            .byte_array_from_slice(&pt)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeGenerateBip39(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let out = match crypto::generate_bip39_24() {
        Ok(words) => json_ok(&words),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeGenerateDeterministicBip39(
    mut env: JNIEnv,
    _class: JClass,
    date_str: JString,
    custom_words_json: JString,
) -> jstring {
    let d = match jstr_to_rust(&mut env, date_str) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let w_json = match jstr_to_rust(&mut env, custom_words_json) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let words: Vec<String> = serde_json::from_str(&w_json).unwrap_or_default();
    let out = match crypto::generate_deterministic_bip39_24(&d, &words) {
        Ok(res) => json_ok(&res),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeValidateBip39(
    mut env: JNIEnv,
    _class: JClass,
    words_json: JString,
) -> jboolean {
    let w_json = match jstr_to_rust(&mut env, words_json) {
        Ok(v) => v,
        Err(_) => return JNI_FALSE,
    };
    let words: Vec<String> = serde_json::from_str(&w_json).unwrap_or_default();
    if crypto::validate_bip39(&words) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeBip39ToSeedHex(
    mut env: JNIEnv,
    _class: JClass,
    words_json: JString,
    passphrase: JString,
) -> jstring {
    let w_json = match jstr_to_rust(&mut env, words_json) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let pass = match jstr_to_rust(&mut env, passphrase) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let words: Vec<String> = serde_json::from_str(&w_json).unwrap_or_default();
    let out = match crypto::bip39_to_seed_hex(&words, &pass) {
        Ok(hex) => json_ok(&hex),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeGenerateX25519Keypair(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let kp = crypto::generate_x25519_keypair();
    to_jstring(&mut env, &json_ok(&kp))
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRatchetInit(
    mut env: JNIEnv,
    _class: JClass,
    contact_id: jint,
    self_priv_b64: JString,
    self_pub_b64: JString,
    remote_pub_b64: JString,
    is_initiator: jboolean,
) -> jstring {
    let sp = match jstr_to_rust(&mut env, self_priv_b64) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let spub = match jstr_to_rust(&mut env, self_pub_b64) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let rpub = match jstr_to_rust(&mut env, remote_pub_b64) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let out = match crypto::ratchet_init(&sp, &spub, &rpub, is_initiator == JNI_TRUE) {
        Ok(state) => {
            vault::set_ratchet_state(contact_id, state.clone());
            json_ok(&state)
        }
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRatchetLoadState(
    mut env: JNIEnv,
    _class: JClass,
    contact_id: jint,
    state_json: JString,
) -> jboolean {
    let raw = match jstr_to_rust(&mut env, state_json) {
        Ok(v) => v,
        Err(_) => return JNI_FALSE,
    };
    match serde_json::from_str::<crypto::RatchetState>(&raw) {
        Ok(state) => {
            vault::set_ratchet_state(contact_id, state);
            JNI_TRUE
        }
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRatchetEncrypt(
    mut env: JNIEnv,
    _class: JClass,
    contact_id: jint,
    plaintext: JByteArray,
) -> jstring {
    let pt = match env.convert_byte_array(plaintext) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e.to_string())),
    };
    let mut state = match vault::get_ratchet_state(contact_id) {
        Some(s) => s,
        None => return to_jstring(&mut env, &json_err("Ratchet state not initialized for contact")),
    };
    let out = match crypto::ratchet_encrypt(&mut state, &pt) {
        Ok(env_msg) => {
            vault::set_ratchet_state(contact_id, state.clone());
            serde_json::json!({
                "ok": true,
                "data": {
                    "envelope": env_msg,
                    "state": state
                }
            })
            .to_string()
        }
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRatchetDecrypt(
    mut env: JNIEnv,
    _class: JClass,
    contact_id: jint,
    envelope_json: JString,
) -> jstring {
    let raw = match jstr_to_rust(&mut env, envelope_json) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let envelope: crypto::RatchetEnvelope = match serde_json::from_str(&raw) {
        Ok(e) => e,
        Err(e) => return to_jstring(&mut env, &json_err(&e.to_string())),
    };
    let mut state = match vault::get_ratchet_state(contact_id) {
        Some(s) => s,
        None => return to_jstring(&mut env, &json_err("Ratchet state not initialized for contact")),
    };
    let out = match crypto::ratchet_decrypt(&mut state, &envelope) {
        Ok(pt) => {
            vault::set_ratchet_state(contact_id, state.clone());
            serde_json::json!({
                "ok": true,
                "data": {
                    "plaintext_b64": B64.encode(&pt),
                    "state": state
                }
            })
            .to_string()
        }
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeShamirSplit(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
    passphrase: JString,
    threshold: jint,
    total: jint,
) -> jstring {
    let bytes = match env.convert_byte_array(payload) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e.to_string())),
    };
    let pass = match jstr_to_rust(&mut env, passphrase) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let out = match crypto::shamir_split_encrypted(&bytes, &pass, threshold as u8, total as u8) {
        Ok(shares) => json_ok(&shares),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeShamirRecover(
    mut env: JNIEnv,
    _class: JClass,
    shares_json: JString,
    passphrase: JString,
) -> jbyteArray {
    let raw = match jstr_to_rust(&mut env, shares_json) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let pass = match jstr_to_rust(&mut env, passphrase) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let shares: Vec<String> = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    match crypto::shamir_recover_encrypted(&shares, &pass) {
        Ok(bytes) => env
            .byte_array_from_slice(&bytes)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeVaultSetSession(
    mut env: JNIEnv,
    _class: JClass,
    alias: JString,
    master_key: JByteArray,
) {
    if let (Ok(a), Ok(k)) = (jstr_to_rust(&mut env, alias), env.convert_byte_array(master_key)) {
        vault::set_active_session(a, k);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeVaultLockAndZeroize(
    _env: JNIEnv,
    _class: JClass,
) {
    vault::lock_and_zeroize_session();
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativePanicZeroizeAll(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    network::relay_disconnect();
    vault::panic_zeroize_all() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeProbeRoute(
    mut env: JNIEnv,
    _class: JClass,
    target_host: JString,
    target_port: jint,
    socks_host: JString,
    socks_port: jint,
    use_socks5: jboolean,
) -> jstring {
    let th = jstr_to_rust(&mut env, target_host).unwrap_or_default();
    let sh = jstr_to_rust(&mut env, socks_host).unwrap_or_default();
    let res = network::probe_route(
        &th,
        target_port as u16,
        &sh,
        socks_port as u16,
        use_socks5 == JNI_TRUE,
    );
    to_jstring(&mut env, &json_ok(&res))
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeProbeSocks5Daemon(
    mut env: JNIEnv,
    _class: JClass,
    socks_host: JString,
    socks_port: jint,
) -> jstring {
    let sh = jstr_to_rust(&mut env, socks_host).unwrap_or_default();
    let res = network::probe_socks5_daemon(&sh, socks_port as u16);
    to_jstring(&mut env, &json_ok(&res))
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRelayConnect(
    mut env: JNIEnv,
    _class: JClass,
    ws_url: JString,
    socks_host: JString,
    socks_port: jint,
    use_socks5: jboolean,
) -> jstring {
    let url = match jstr_to_rust(&mut env, ws_url) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let sh = jstr_to_rust(&mut env, socks_host).unwrap_or_default();
    let socks_opt = if use_socks5 == JNI_TRUE && !sh.is_empty() {
        Some(sh.as_str())
    } else {
        None
    };
    let out = match network::relay_connect(&url, socks_opt, socks_port as u16) {
        Ok(()) => json_ok(&"Connecting"),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRelaySendFrame(
    mut env: JNIEnv,
    _class: JClass,
    frame_json: JString,
) -> jstring {
    let frame = match jstr_to_rust(&mut env, frame_json) {
        Ok(v) => v,
        Err(e) => return to_jstring(&mut env, &json_err(&e)),
    };
    let out = match network::relay_send_frame(&frame) {
        Ok(()) => json_ok(&"Queued"),
        Err(e) => json_err(&e),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRelayDisconnect(
    _env: JNIEnv,
    _class: JClass,
) {
    network::relay_disconnect();
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRelayStatus(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let st = network::relay_status();
    to_jstring(&mut env, &json_ok(&st))
}

#[no_mangle]
pub extern "system" fn Java_com_example_rust_MarcianoRustCore_nativeRelayDrainEvents(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let ev = network::relay_drain_events();
    to_jstring(&mut env, &json_ok(&ev))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::net::TcpListener;
    use tokio_tungstenite::accept_async;
    use futures_util::{SinkExt, StreamExt};

    #[test]
    fn test_argon2_pin_hash_and_verify() {
        let rec = crypto::hash_pin_argon2("482910").expect("hash");
        assert!(crypto::verify_pin_argon2("482910", &rec.phc_hash));
        assert!(!crypto::verify_pin_argon2("000000", &rec.phc_hash));
    }

    #[test]
    fn test_aes_gcm_roundtrip_and_tamper() {
        let key = crypto::random_bytes(32);
        let enc = crypto::encrypt_aes_gcm(b"Mars matrix secret", &key).expect("enc");
        let dec = crypto::decrypt_aes_gcm(&enc.ciphertext_b64, &enc.iv_b64, &key).expect("dec");
        assert_eq!(dec, b"Mars matrix secret");

        let mut wrong_key = key.clone();
        wrong_key[0] ^= 0xFF;
        assert!(crypto::decrypt_aes_gcm(&enc.ciphertext_b64, &enc.iv_b64, &wrong_key).is_err());
    }

    #[test]
    fn test_bip39_24_words_and_deterministic() {
        let words = crypto::generate_bip39_24().expect("bip39");
        assert_eq!(words.len(), 24);
        assert!(crypto::validate_bip39(&words));

        let custom = vec![
            "alpha".into(),
            "mars".into(),
            "neon".into(),
            "cipher".into(),
            "vault".into(),
        ];
        let det1 = crypto::generate_deterministic_bip39_24("2026-11-20", &custom).expect("det1");
        let det2 = crypto::generate_deterministic_bip39_24("2026-11-20", &custom).expect("det2");
        assert_eq!(det1, det2);
        assert!(crypto::validate_bip39(&det1));

        let seed = crypto::bip39_to_seed_hex(&det1, "pass").expect("seed");
        assert_eq!(seed.len(), 128);
    }

    #[test]
    fn test_x25519_double_ratchet_forward_secrecy() {
        let alice_kp = crypto::generate_x25519_keypair();
        let bob_kp = crypto::generate_x25519_keypair();

        let mut alice = crypto::ratchet_init(
            &alice_kp.private_b64,
            &alice_kp.public_b64,
            &bob_kp.public_b64,
            true,
        )
        .expect("alice ratchet");
        let mut bob = crypto::ratchet_init(
            &bob_kp.private_b64,
            &bob_kp.public_b64,
            &alice_kp.public_b64,
            false,
        )
        .expect("bob ratchet");

        let env1 = crypto::ratchet_encrypt(&mut alice, b"Hello Bob #1").expect("enc1");
        let env2 = crypto::ratchet_encrypt(&mut alice, b"Hello Bob #2").expect("enc2");
        assert_ne!(env1.ciphertext_b64, env2.ciphertext_b64);

        let pt1 = crypto::ratchet_decrypt(&mut bob, &env1).expect("dec1");
        let pt2 = crypto::ratchet_decrypt(&mut bob, &env2).expect("dec2");
        assert_eq!(pt1, b"Hello Bob #1");
        assert_eq!(pt2, b"Hello Bob #2");

        // Replay must be rejected
        assert!(crypto::ratchet_decrypt(&mut bob, &env1).is_err());
    }

    #[test]
    fn test_shamir_3_of_5_encrypted_recovery() {
        let secret = b"MARCIANOX_FULL_VAULT_BACKUP_PAYLOAD_2026";
        let shares = crypto::shamir_split_encrypted(secret, "strong_pass_99", 3, 5).expect("split");
        assert_eq!(shares.len(), 5);

        // Recover using shares 1, 3, 5
        let subset = vec![
            shares[0].share_b64.clone(),
            shares[2].share_b64.clone(),
            shares[4].share_b64.clone(),
        ];
        let recovered = crypto::shamir_recover_encrypted(&subset, "strong_pass_99").expect("recover");
        assert_eq!(recovered, secret);

        // Wrong passphrase fails
        assert!(crypto::shamir_recover_encrypted(&subset, "wrong_pass").is_err());
    }

    #[test]
    fn test_real_tokio_websocket_relay_loopback() {
        let port: u16 = network::runtime().block_on(async {
            let listener = TcpListener::bind("127.0.0.1:0").await.expect("bind");
            let p = listener.local_addr().unwrap().port();
            tokio::spawn(async move {
                if let Ok((stream, _)) = listener.accept().await {
                    let mut ws = accept_async(stream).await.expect("ws accept");
                    if let Some(Ok(msg)) = ws.next().await {
                        let _ = ws.send(msg).await;
                    }
                }
            });
            p
        });

        let url = format!("ws://127.0.0.1:{}", port);
        network::relay_connect(&url, None, 9050).expect("relay_connect");
        std::thread::sleep(std::time::Duration::from_millis(150));
        assert!(network::relay_status().connected);

        network::relay_send_frame(r#"{"type":"SEND","body":"PING_RUST"}"#).expect("send");
        std::thread::sleep(std::time::Duration::from_millis(150));

        let events = network::relay_drain_events();
        assert!(events.iter().any(|e| e.event_type == "MESSAGE" && e.payload.contains("PING_RUST")));
        network::relay_disconnect();
    }
}
